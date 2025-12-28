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
import io.syspulse.skel.FutureAwaitable
import os.{read => engines}


abstract class Script(val id:Script.ID,val name:String) {
  protected val log = Logger(s"${this.getClass()}")

  override def toString: String = s"${this.getClass().getSimpleName()}"

  def getId():Script.ID = this.id
  def run(src:String,input:String,data:Map[String,Any]):Try[String]
  
  // Async version of run() - returns Future[String]
  // For blocking scripts, uses a dedicated thread pool
  // For ScriptAI, uses promptAsync with tools and images from data Map
  def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[String] = {
    // Default implementation: wrap blocking run() in Future using blocking pool
    Future {
      run(src, input, data).get
    }(Script.blockingEc)
  }
}


// --- Json Query ---------------------------------------------------------------
class ScriptJQ(src0:Option[String]) extends Script("jq","json-query") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    val expr = if(src.isBlank) src0.getOrElse("") else src

    Util
      .walkJson(input,expr,true)
      .map(r => r.map(e => e.toString).mkString(","))

  }
}

object ScriptJQ {
  def build(src:Option[String]):Script = new ScriptJQ(src)
}

class ScriptJQScore(src0:Option[String]) extends ScriptJQ(src0) {
  override val id:String = "jq_score"
  override val name:String = "json-query-score"
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    super.run(src,input,data) match {
      case Success(r) if(r.isBlank) => Success("0.0")
      case Success(r) => Success("1.0")
      case Failure(e) => Failure(e)
    }
  }
}

object ScriptJQScore {
  def build(src:Option[String]):Script = new ScriptJQScore(src)
}

// --- Solidity Query ---------------------------------------------------------------
class ScriptSQ(src0:Option[String] = None) extends Script("sq","solidity-query") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    SolidityResult
      .extractString(input,src)
      .map(r => r.toString)
  }
}

object ScriptSQ {
  def build(src:Option[String]):Script = new ScriptSQ(src)
}

class ScriptSQScore(src0:Option[String]) extends ScriptSQ(src0) {
  override val id:String = "sq_score"
  override val name:String = "solidity-query-score"
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    super.run(src,input,data) match {
      case Success(r) if(r.isBlank) => Success("0.0")
      case Success(r) => Success("1.0")
      case Failure(e) => Failure(e)
    }
  }
}

object ScriptSQScore {
  def build(src:Option[String]):Script = new ScriptSQScore(src)
}

// --- Js --------------------------------------------------------------------------
class ScriptJS(src0:Option[String] = None,inputVarName:String = "input") extends Script("js","javascript") {  
  // src0 is either a script or a reference to a file with a script
  private val script0 = src0.map(s => if(s.startsWith("file://")) os.read(os.Path(s.stripPrefix("file://"),os.pwd)) else s)

  private lazy val engine = new Polyglot("js",PolyglotSandbox.RESTRICTED_THREADED,script0)
  
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    // try {
    //   val dataInput = data + (inputVarName -> input)
    //   val r = engine.run(src,dataInput)
    //   r match {
    //     case null => Failure(new Exception("result: null"))
    //     case r => Success(r.toString)
    //   }
    // } catch {
    //   // case e:jdk.nashorn.internal.runtime.ECMAException => Failure(e)
    //   // case e:javax.script.ScriptException => Failure(e)
    //   // case e:Exception => Failure(e)
    //   case e:Throwable => Failure(e)
    // }
    val dataInput = data + (inputVarName -> input)
    engine.run(src,dataInput) match {
      case Success(null) => Failure(new Exception("result: null"))
      case Success(r) => Success(r.toString)
      case Failure(e) => Failure(e)      
    }
  }
}

object ScriptJS {
  def build(src0:Option[String]):Script = new ScriptJS(src0 = src0)
}

// --- String --------------------------------------------------------------------------
class ScriptStr extends Script("str","string") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {    
    Success(input)
  }
}

object ScriptStr {
  val STR = new ScriptStr()
  def build(src:Option[String]):Script = STR
}

// --- String --------------------------------------------------------------------------
class ScriptNone extends Script("none","") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {    
    Success("")
  }
}

object ScriptNone {
  val NONE = new ScriptNone()
  def build(src:Option[String]):Script = NONE
}

// --- Sleep Test --------------------------------------------------------------------------
class ScriptSleepTest(sleepTime:Int) extends Script("sleep-test","sleep-test") {
  def this() = this(0) // Default constructor for ScriptBuilder
  
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    Try {
      val msec = if(sleepTime > 0) sleepTime else input.toInt
      Thread.sleep(msec)
      input
    }
  }

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[String] = {
    Future {
      val msec = if(sleepTime > 0) sleepTime else input.toInt
      Thread.sleep(msec)
      input
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

  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    if(input.isBlank) return Success(input)
    if(src.isBlank && expr0.isEmpty) return Success(input)
    
    val expr:Expr = if(src.isBlank && expr0.isDefined) expr0.get else parse(src)
    expr.run(input,data)
  }
}

object ScriptRegexp {
  def build(src:Option[String]):Script = new ScriptRegexp(src)
}

// --- Regexp Score ---------------------------------------------------------------
class ScriptRegexpScore(src0:Option[String]) extends ScriptRegexp(src0) {
  override val id:String = "regexp_score"
  override val name:String = "regexp-jvm-score"
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {

    super.run(src,input,data) match {
      case Success(r) if(r.isBlank) => Success("0.0")
      case Success(r) => Success("1.0")
      case Failure(e) => Failure(e)
    }
  }
}

object ScriptRegexpScore {
  def build(src:Option[String]):Script = new ScriptRegexpScore(src)
}

// --- AI Query ---------------------------------------------------------------
class ScriptAI(src0:Option[String]) extends Script("ai","ai-llm") {
  val aiUri = AiURI(src0.getOrElse(ScriptAI.DEF_AI_URI))
  val provider:AiProvider = AiProvider(aiUri)
  
  // Dedicated execution context for AI operations - created once per instance
  implicit val aiEc: ExecutionContext = ScriptAI.aiExecutionContext
      
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    val timeout = data.get("timeout").map(_.asInstanceOf[Long]).getOrElse(aiUri.timeout)
    FutureAwaitable.awaitTry(exec(src,input,data))(timeout)
  }

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[String] = {
    // Use the dedicated execution context for AI operations (ignore parameter)
    if(input.isBlank) 
      return Future.successful(input)
    
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
    val images: Seq[String] = data.get("images") match {
      case Some(img: Seq[String] @unchecked) if img.nonEmpty && img.head.isInstanceOf[String] => 
        img.asInstanceOf[Seq[String]]
      case Some(img: Seq[_]) => 
        img.collect { case s: String => s }
      case Some(img: String) => Seq(img)
      case _ => Seq.empty
    }

    val outputType: Option[String] = data.get("output").map(_.toString)
    
    val a0 = Ai(
      question = input,
      model = aiUri.getModel(),
      xid = aiUri.tid
    )
    
    provider.promptAsync(a0, aiUri.system, aiUri.timeout, aiUri.retry, tools, images, outputType)(aiEc)
      .map(_.answer.getOrElse(""))(aiEc)
  }
}

object ScriptAI {
  val DEF_AI_URI = "openrouter://arcee-ai/trinity-mini:free"
  
  // Dedicated execution context for AI operations using standard thread pool
  val aiExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors() * 2)
  )
  
  def build(src:Option[String]):Script = new ScriptAI(src)
}

// --- Flow ---------------------------------------------------------------
class ScriptFlow(flow:Seq[Script]) extends Script("flow","flow") {

  override def toString: String = s"${this.getClass().getSimpleName()}(${flow.map(_.toString).mkString(",")})"

  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    flow.foldLeft[Try[String]](Success(input)) { (result,engine) =>
      result.flatMap(r => engine.run(src,r,data))
    }
  }

  override def exec(src:String,input:String,data:Map[String,Any])(implicit ec: ExecutionContext):Future[String] = {
    flow.foldLeft[Future[String]](Future.successful(input)) { (result,engine) =>
      result.flatMap(r => engine.exec(src, r, data))
    }
  }
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
      case "ai" :: src :: Nil => Try(new ScriptAI(Some(src)))
      
      case "js" :: Nil => 
        //log.warn(s"js:// not supported without script")
        Failure(new Exception("js:// not supported without script"))
      case "js" :: rest =>                
        Try(new ScriptJS(Some(uri.stripPrefix("js://"))))
      case "str" :: _ => Success(new ScriptStr())
      case src =>         
        Failure(new Exception(s"Unknown script URI: '${uri}'"))
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

  type ID = String //UUID

  val SCRIPT_STR = new ScriptStr()

  // Dedicated thread pool for blocking Futures using standard executor
  val blockingEc: ExecutionContext = ExecutionContext.fromExecutorService(
    Executors.newCachedThreadPool()
  )


}
