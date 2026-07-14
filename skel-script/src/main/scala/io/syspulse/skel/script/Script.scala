package io.syspulse.skel.script

import scala.util.Random
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger
import scala.util.{Success,Failure,Try}
import scala.util.matching.Regex
import scala.util.matching.Regex
import scala.concurrent.{Future,ExecutionContext}
import java.util.concurrent.Executors

import io.syspulse.skel.FutureAwaitable

import io.syspulse.skel.dsl.{Polyglot,PolyglotSandbox}
import io.syspulse.skel.crypto.eth.SolidityResult
import io.syspulse.skel.util.Util

import io.syspulse.skel.ai.core.Providers
import io.syspulse.skel.ai.core.{AiURI,AiTool}
import io.syspulse.skel.ai.provider.AiProvider
import io.syspulse.skel.ai.Ai
import io.syspulse.skel.FutureUtil
import os.{read => engines}
import scala.concurrent.Await
import io.syspulse.skel.util.ConditionDouble
import io.syspulse.skel.uri.HttpURI
import io.syspulse.skel.HTTP
import akka.http.scaladsl.model.HttpMethods


abstract class Script(val id:Script.ID,val name:String) {
  protected val log = Logger(s"${this.getClass()}")

  override def toString: String = s"${this.getClass().getSimpleName()}"

  def getId():Script.ID = this.id

  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]]

  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {    
    val f = exec(src,input,data)(Script.blockingEc)

    val r = f.map(m => m.get("result").map(_.toString).get)(Script.blockingEc)
    
    FutureUtil.sync(r)(Script.timeout(data))
      
  }
}


// --- Json Query ---------------------------------------------------------------
class ScriptJQ(src0:Option[String]) extends Script("jq","json-query") {
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {
    val expr = if(src.isBlank) src0.getOrElse("") else src

    Util
      .walkJson(input,expr,true)
      .map(r => r.map(e => e.toString).mkString(","))
      .map(r => data + ("result" -> r))
   
  }
}

object ScriptJQ {
  def build(src:Option[String]):Script = new ScriptJQ(src)
}

class ScriptJQScore(src0:Option[String]) extends ScriptJQ(src0) {
  override val id:String = "jq_score"
  override val name:String = "json-query-score"
  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    super.exec(src,input,data)
    .map(r => r.get("result"))
    .map {
      case Some(r) if r.toString.isBlank => "0.0"
      case _ => "1.0"
    }
    .map(r => data + ("result" -> r))
  }
}

object ScriptJQScore {
  def build(src:Option[String]):Script = new ScriptJQScore(src)
}

// --- Solidity Query ---------------------------------------------------------------
class ScriptSQ(src0:Option[String] = None) extends Script("sq","solidity-query") {
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {
    SolidityResult
      .extractString(input,src)
      .map(r => r.toString)
      .map(r => data + ("result" -> r))
  }
}

object ScriptSQ {
  def build(src:Option[String]):Script = new ScriptSQ(src)
}

class ScriptSQScore(src0:Option[String]) extends ScriptSQ(src0) {
  override val id:String = "sq_score"
  override val name:String = "solidity-query-score"
  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    super.exec(src,input,data)
    .map(r => r.get("result"))
    .map {
      case Some(r) if r.toString.isBlank => "0.0"
      case _ => "1.0"
    }
    .map(r => data + ("result" -> r))
  }
}

object ScriptSQScore {
  def build(src:Option[String]):Script = new ScriptSQScore(src)
}

// --- Js --------------------------------------------------------------------------
class ScriptJS(src0:Option[String] = None,inputVarName:String = "input") extends Script("js","javascript") {  
  // src0 is either a script or a reference to a file with a script
  private val script0 = src0.map(s => if(s.startsWith("file://")) os.read(os.Path(s.stripPrefix("file://"),os.pwd)) else s)

  //private lazy val engine = new Polyglot("js",PolyglotSandbox.RESTRICTED_THREADED,script0,true)
  private lazy val engine = new Polyglot("js",PolyglotSandbox.RESTRICTED,script0,true)
  
  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {
    val dataInput = data + (inputVarName -> input)
    engine.run(src,dataInput) match {
      case Success(null) => 
        //Failure(new Exception("result: null"))
        Failure(new Script.ScriptBreakException("null"))
      case Success(r) =>
        val exported = engine.exportBindings(dataInput.keySet)
        Success(data ++ exported + ("result" -> r.toString))
      case Failure(e) => Failure(e)      
    }
  }
}

object ScriptJS {
  def build(src0:Option[String]):Script = new ScriptJS(src0 = src0)
}

// --- String --------------------------------------------------------------------------
class ScriptStr(src0:Option[String] = None) extends Script("str","string") {
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] =
    Future.successful {
      val r = Util.replaceVar(src0.getOrElse("{input}"),Map("input" -> input))
      data + ("result" -> r)
    }
}

object ScriptStr {  
  def build(src:Option[String]):Script = new ScriptStr(src)
}


// --- None --------------------------------------------------------------------------
class ScriptNone extends Script("none","") {
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] =
    Future.successful(data + ("result" -> ""))
}

object ScriptNone {
  val NONE = new ScriptNone()
  def build(src:Option[String]):Script = NONE
}

// --- Sleep Test --------------------------------------------------------------------------
class ScriptSleepTest(sleepTime:Int) extends Script("sleep-test","sleep-test") {
  def this() = this(0) // Default constructor for ScriptBuilder

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    Future {
      val msec = if(sleepTime > 0) sleepTime else input.toInt
      Thread.sleep(msec)
      data + ("result" -> msec)
    }(Script.blockingEc)
  }
}

object ScriptSleepTest {
  val SLEEP_TEST = new ScriptSleepTest()
  def build(src:Option[String]):Script = SLEEP_TEST
}

// --- Regexp --------------------------------------------------------------------------
class ScriptRegexp(src0:Option[String]) extends Script("regexp","regexp-jvm") {

  abstract class Expr(expr:String) {
    def run(input:String,data:Map[String,Any]):Try[String]        
  }

  class ExprEmpty() extends Expr("") {
    def run(input:String,data:Map[String,Any]):Try[String] = {
      Success(input)
    }
  }

  class ExprMatch(expr:String) extends Expr(expr) {
    val pattern:Regex = expr.r
    def run(input:String,data:Map[String,Any]):Try[String] = {
      Try {
        if (pattern.matches(input)) input else ""
      }
    }
  }
  
  class ExprNotMatch(expr:String) extends ExprMatch(expr) {
    override def run(input:String,data:Map[String,Any]):Try[String] = {
      Try {
        if (! pattern.matches(input)) input else ""
      }
    }
  }
  case class ExpExtract(expr:String) extends ExprMatch(expr) {
    override def run(input:String,data:Map[String,Any]):Try[String] = {
      Try {
        val m = pattern.findFirstMatchIn(input)
        if(m.isDefined) m.get.group(1) else ""
      }
    }
  }

  val exprEmty = new ExprEmpty()

  def parse(expr0:String):Expr = {
    if(expr0.isBlank) return exprEmty
    val expr = expr0.trim
    if(expr.startsWith("?")) return new ExpExtract(expr.substring(1))
    if(expr.startsWith("!")) return new ExprNotMatch(expr.substring(1))    
    new ExprMatch(expr)
  }

  val expr0:Option[Expr] = src0.map(parse)

  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {
    val r = 
      if(input.isBlank) Success(input)
      else 
        if(src.isBlank && expr0.isEmpty) Success(input)
      else {    
        val expr:Expr = if(src.isBlank && expr0.isDefined) expr0.get else parse(src)
        expr.run(input,data)
      }
    r.map(r => data + ("result" -> r))
  }
}

object ScriptRegexp {
  def build(src:Option[String]):Script = new ScriptRegexp(src)
}

// --- Regexp Score ---------------------------------------------------------------
class ScriptRegexpScore(src0:Option[String]) extends ScriptRegexp(src0) {
  override val id:String = "regexp_score"
  override val name:String = "regexp-jvm-score"
  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    super.exec(src,input,data)
    .map(r => r.get("result"))
    .map {
      case Some(r) if r.toString.isBlank => "0.0"
      case _ => "1.0"
    }
    .map(r => data + ("result" -> r))
  }
}

object ScriptRegexpScore {
  def build(src:Option[String]):Script = new ScriptRegexpScore(src)
}

// --- AI Query ---------------------------------------------------------------
// src0 - Prompt !
class ScriptAI(prompt0:Option[String],uri0:Option[String] = None) extends Script("ai","ai-llm") {
  val aiUri = AiURI(uri0.getOrElse(ScriptAI.DEF_AI_URI))
  val provider:AiProvider = AiProvider(aiUri)
  
  // Dedicated execution context for AI operations - created once per instance
  implicit val aiEc: ExecutionContext = ScriptAI.aiExecutionContext
      
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    val timeout = data.get("timeout").map(_.asInstanceOf[Long]).getOrElse(aiUri.timeout)    
    val f = exec(src,input,data)(Script.blockingEc)
    val r = f.map(r => r.get("result").map(_.toString).get)(Script.blockingEc)
    FutureUtil.sync(r)(timeout)
  }

  def extractImages(input:String):(Seq[String],String) = {
    val imageUrlPattern = """image://([^\s]+)""".r
    val imagesInInput = imageUrlPattern.findAllMatchIn(input).map(m => m.group(1)).toSeq
    val inputWithoutImages = imageUrlPattern.replaceAllIn(input, "")
    (imagesInInput,inputWithoutImages)
  }

  def extractOutput(input:String):(Option[String],String) = {
    val outputUrlPattern = """output://([^\s]+)""".r
    val outputInInput = outputUrlPattern.findAllMatchIn(input).map(m => m.group(1)).toSeq.headOption
    val inputWithoutOutput = outputUrlPattern.replaceAllIn(input, "")
    (outputInInput,inputWithoutOutput)
  }

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    // Use the dedicated execution context for AI operations (ignore parameter)
    if(input.isBlank && prompt0.isEmpty && src.isBlank) 
      return Future.successful(data + ("result" -> input))

    val prompt1 = if(prompt0.isDefined && !prompt0.get.isBlank) prompt0.get else src
    val prompt2 = Util.replaceVar(prompt1,Map("input" -> input) ++ data)
    
    // Extract image:// and output:// from prompt itself
    val (imagesInPrompt,prompt3) = extractImages(prompt2)
    val (outputInPrompt,prompt) = extractOutput(prompt3)
    
    if(prompt.isBlank) {
      log.warn(s"Prompt is empty: input='${input}'")
      return Future.successful(data + ("result" -> input))
    }    
        
    // Extract tools from data Map
    val tools: Seq[AiTool] = data.get("tools") match {
      case Some(t: Seq[AiTool] @unchecked) if t.nonEmpty && t.head.isInstanceOf[AiTool] => 
        t.asInstanceOf[Seq[AiTool]]
      case Some(t: Seq[_]) => 
        t.collect { 
          case tool: AiTool => tool
          case m: Map[_, _] @unchecked => 
            // Try to convert Map to AiTool
            try {
              val map = m.asInstanceOf[Map[String, Any]]
              AiTool(
                `type` = map.getOrElse("type", "").toString,
                name = map.get("name").map(_.toString),
                description = map.get("description").map(_.toString),
                parameters = map.get("parameters").map(_.asInstanceOf[Map[String, Any]]),
                strict = map.get("strict").map(_.asInstanceOf[Boolean])
              )
            } catch {
              case _: Exception => null
            }
        }.filter(_ != null)
      case _ => Seq.empty
    }
    
    // Extract images from data Map
    val imagesInData: Seq[String] = data.get("images") match {
      case Some(img: Seq[String] @unchecked) if img.nonEmpty && img.head.isInstanceOf[String] => 
        img.asInstanceOf[Seq[String]]
      case Some(img: Seq[_]) => 
        img.collect { case s: String => s }
      case Some(img: String) => Seq(img)
      case _ => Seq.empty
    }

    val outputType: Option[String] = data.get("output").map(_.toString).orElse(outputInPrompt)
    val images = imagesInPrompt

    val a0 = Ai(
      question = prompt,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )

    log.debug(s"${aiUri}: ${provider}: prompt='${prompt}', images=${images}, outputType=${outputType}")
    
    provider
      .promptAsync(a0, aiUri.system, aiUri.timeout, aiUri.retry, tools, images, outputType)(aiEc)
      .map(_.answer.getOrElse(""))(aiEc)
      .map(r => data + ("result" -> r))(aiEc)
  }
}

object ScriptAI {
  // val DEF_AI_URI = "openrouter://arcee-ai/trinity-mini:free"
  val DEF_AI_URI = "mirror://parrot"
  
  // Dedicated execution context for AI operations using standard thread pool
  val aiExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors() * 2)
  )
  
  def build(src:Option[String]):Script = new ScriptAI(src)
}

// --- Filter --------------------------------------------------------------------------
class ScriptFilter(src0:Option[String] = None) extends Script("filter","filter") {  
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {    
    if(!input.isBlank) 
      Success(data + ("result" -> input))
    else {
      // Use src0 from URI if provided, otherwise use src parameter from run()
      //val srcValue = if(src0.isDefined && !src0.get.isBlank) src0.get else src
      Failure(new Script.ScriptBreakException(input))
    }
  }
}

object ScriptFilter {
  //class ScriptFilterException(val src:String) extends Exception  
  def build(src:Option[String]):Script = new ScriptFilter(src)
}

// --- Condition --------------------------------------------------------------------------
class ScriptCondition(src0:Option[String] = None) extends Script("condition","condition") {  
  val c = new ConditionDouble(0.0,src0.getOrElse(""))

  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = Future.fromTry {    
    if(input.isBlank) 
      Failure(new Script.ScriptBreakException(c.condition))
    else {
      c.set(input.toDouble) match {
        case true => Success(data + ("result" -> input))
        case false => Failure(new Script.ScriptBreakException(c.condition))
      }      
    }
  }
}

object ScriptCondition {  
  def build(src:Option[String]):Script = new ScriptCondition(src)
}

// --- Script API call ---------------------------------------------------- 
// src - body with "{placeholders}"
// 
class ScriptApi(body0:Option[String],uri0:Option[String] = None) extends Script("api","ai-llm") {
  val uri = HttpURI(uri0.getOrElse(""))
    
  // Dedicated execution context for AI operations - created once per instance
  implicit val ec: ExecutionContext = ScriptApi.ec
      
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    val timeout = data.get("timeout").map(_.asInstanceOf[Long]).getOrElse(ScriptApi.DEF_TIMEOUT)
    val f = exec(src, input, data).map(_.get("result").map(_.toString).get)(Script.blockingEc)
    FutureUtil.sync(f)(timeout)
  }
  
  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {

    val body1 = if(body0.isDefined && !body0.get.isBlank) body0.get else src

    // parse the source into optional HEADERS and BODY sections (structure first, then substitute)
    val (parsedHeaders,bodyTemplate) = ScriptApi.parseSections(body1)

    val vars = Map[String,Any]("input" -> input) ++ data

    val body = bodyTemplate.map(b => Util.replaceVar(b,vars)).filter(!_.isBlank)

    val headersFromBody = parsedHeaders.map{ case(k,v) => (Util.replaceVar(k,vars),Util.replaceVar(v,vars)) }

    val headers = uri.headers.toSeq ++ headersFromBody

    val timeout = data.get("timeout").map(_.asInstanceOf[Long]).getOrElse(ScriptApi.DEF_TIMEOUT)

    val verb = uri.verb match {
      case "GET" => HttpMethods.GET
      case "POST" => HttpMethods.POST
      case "PUT" => HttpMethods.PUT
      case "DELETE" => HttpMethods.DELETE
      case _ => HttpMethods.GET
    }

    val url = Util.replaceVar(uri.uri, vars)
    
    log.info(s"body='${Util.trunc(body.getOrElse(""),64)}' ==> ${uri.verb}(${url}), headers=${headers.map(_._1)}")
    
    val f = HTTP.req(url, verb, body, headers, timeout = timeout)
    f.map(r => data + ("result" -> r))
  }
}

object ScriptApi {   
  val DEF_TIMEOUT:Long = 10000L

  val HEADERS_MARKER:String = "HEADERS"
  val BODY_MARKER:String = "BODY"

  // Dedicated execution context for API operations using standard thread pool
  val ec: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors() * 1)
  )
  
  def build(src:Option[String]):Script = new ScriptApi(src)

  // Parse the source into optional HEADERS section and BODY section.
  //
  //   HEADERS
  //   key: value
  //   key: value
  //
  //   BODY
  //   {input}
  //
  // If no HEADERS marker is present, the whole text is treated as BODY.
  // {input} (and other {vars}) placeholders are resolved by the caller after parsing.
  def parseSections(text:String):(Seq[(String,String)],Option[String]) = {
    if(text == null || text.isBlank) return (Seq.empty,None)

    val lines = text.split("\r?\n", -1).toList
    val firstIdx = lines.indexWhere(_.trim.nonEmpty)

    // no HEADERS section: everything is the body
    if(firstIdx < 0 || lines(firstIdx).trim != HEADERS_MARKER)
      return (Seq.empty, Some(text))

    val headers = scala.collection.mutable.ArrayBuffer[(String,String)]()
    var i = firstIdx + 1
    var bodyStart = -1
    while(i < lines.size && bodyStart < 0) {
      val line = lines(i)
      if(line.trim == BODY_MARKER) {
        bodyStart = i + 1
      } else if(line.trim.nonEmpty) {
        // header key/value are separated by ':' (HTTP style), split on the first ':' only
        val idx = line.indexOf(':')
        if(idx > 0) {
          val k = line.substring(0,idx).trim
          val v = line.substring(idx + 1).trim
          if(k.nonEmpty) headers += ((k,v))
        }
        // else ignore malformed header line
      }
      i += 1
    }

    val body =
      if(bodyStart >= 0) {
        val b = lines.drop(bodyStart).mkString("\n").trim
        if(b.isEmpty) None else Some(b)
      } else None

    (headers.toSeq, body)
  }
}


// --- Flow ---------------------------------------------------------------
class ScriptFlow(flow:Seq[Script]) extends Script("flow","flow") {

  override def toString: String = s"${this.getClass().getSimpleName()}(${flow.map(_.toString).mkString(",")})"

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[Map[String,Any]] = {
    flow.foldLeft[Future[Map[String,Any]]](Future.successful(data + ("result" -> input))) { (result,engine) =>
      result.flatMap { d =>
        val input = d.get("result").map(_.toString).getOrElse("")
        log.info(s"${engine.name}: input='${input}', data=${d}")
        engine.exec(src, input, d)
      }
    }

    //   // Note: Short-circuiting happens naturally - when engine.exec returns a failed Future,
    //   // flatMap doesn't execute the function, so foldLeft stops processing remaining engines.
    //   // The final .recover handles converting ScriptFilter.ScriptFilterBypass to empty string.
    // }.recover { 
    //   case e: Script.ScriptBreakException => 
    //     //e.src
    //     throw e
    //   case e: Exception => throw e
    // }
  }

  def size():Int = flow.size
}

object ScriptFlow {
  private val log = Logger(s"${this.getClass()}")

  def build(flow:Seq[Script]):Script = new ScriptFlow(flow)

  def parseUri(uri:String):Try[Script] = {    
    if(uri.isBlank()) return Failure(new Exception(s"Invalid script URI: '${uri}'"))
    
    uri.split("://").toList match {
      case "jq_score" :: src :: Nil => Try(new ScriptJQScore(Some(src)))
      case "jq" :: src :: Nil => Try(new ScriptJQ(Some(src)))
      case "sq_score" :: src :: Nil => Try(new ScriptSQScore(Some(src)))
      case "sq" :: src :: Nil => Try(new ScriptSQ(Some(src)))
      case "regexp_score" :: src :: Nil => Try(new ScriptRegexpScore(Some(src)))
      case "regex_score" :: src :: Nil => Try(new ScriptRegexpScore(Some(src)))
      case "regexp" :: src :: Nil => Try(new ScriptRegexp(Some(src)))
      case "ai" :: prompt :: Nil => Try(new ScriptAI(Some(prompt)))
      case "filter" :: src :: Nil => Try(new ScriptFilter(Some(src)))
      case "filter" :: Nil => Success(new ScriptFilter(None))

      case "condition" :: src :: Nil => Try(new ScriptCondition(Some(src)))      
      
      case "js" :: Nil => 
        //log.warn(s"js:// not supported without script")
        Failure(new Exception("js:// not supported without script"))
      case "js" :: rest =>                
        Try(new ScriptJS(Some(uri.stripPrefix("js://"))))
      case "str" :: _ => Success(new ScriptStr())

      case "api" :: uri :: Nil => Try(new ScriptApi(None,Some(uri)))
      case "api" :: uri :: body :: Nil => Try(new ScriptApi(Some(body),Some(uri)))

      case src =>         
        Failure(new Exception(s"Unknown script URI: '${uri}'"))
    }      
  }

  def resolve(typ:String,src:String,opts:Option[String]):Try[Script] = {    
    if(typ.isBlank()) return Failure(new Exception(s"Invalid script URI: '${typ}'"))
    
    typ.trim match {
      case "jq_score" => Try(new ScriptJQScore(Some(src)))
      case "jq" => Try(new ScriptJQ(Some(src)))
      case "sq_score" => Try(new ScriptSQScore(Some(src)))
      case "sq" => Try(new ScriptSQ(Some(src)))
      case "regexp_score" => Try(new ScriptRegexpScore(Some(src)))
      case "regex_score" => Try(new ScriptRegexpScore(Some(src)))
      case "regexp" => Try(new ScriptRegexp(Some(src)))
      case "ai" => Try(new ScriptAI(prompt0 = Some(src),uri0 = opts))
      case "filter" => Try(new ScriptFilter(Some(src)))
      case "js" =>  Try(new ScriptJS(Some(src)))
      case "condition" => Try(new ScriptCondition(Some(src)))
      case "str"  => Success(new ScriptStr(Some(src)))
      case "api" => Try(new ScriptApi(Some(src),uri0 = opts))
      case _ => Failure(new Exception(s"Unknown script type: '${typ}'"))
    }      
  }

  def build(flow:Option[String]):Script = {    
    if(flow.isEmpty) return new ScriptFlow(Seq.empty)

    val engines = flow
      .get
      .split(",")
      .filter(! _.isBlank())      
      .flatMap(e => {
        parseUri(e.trim) match {
          case Success(s) => Some(s)
          case Failure(e) => 
            log.warn(s"Failed to parse script URI: '${e}': '${e.getMessage()}'")
            None
        }
       })
      .toSeq
    
    new ScriptFlow(engines)
  }
}

// === Engine ====================================================================================
object Script {
  val log = Logger(s"${this.getClass()}")

  val DEFAULT_TIMEOUT:Long = 10000L

  def timeout(data:Map[String,Any]):Long = {
    data.get("timeout") match {
      case Some(t: Long) => t
      case Some(t: Int) => t.toLong
      case Some(t: String) => t.toLong
      case Some(t) => t.toString.toLong
      case None => DEFAULT_TIMEOUT
    }
  }

  // skip script flow with who initiated
  class ScriptBreakException(val src:String) extends Exception {
    override def getMessage():String = s"Break: '${src}'"
  }

  type ID = String //UUID

  val SCRIPT_STR = new ScriptStr()

  // Dedicated thread pool for blocking Futures using standard executor
  val blockingEc: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newCachedThreadPool()
  )


}
