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


abstract class Script(id:Script.ID,name:String) {
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

trait ScriptBuilder {
  def build(src:Option[String]):Script
}

// --- Json Query ---------------------------------------------------------------
class ScriptJQ(src0:Option[String]) extends Script("jq","json-query") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    Util
      .walkJson(input,src,true)
      .map(r => r.toString)
  }
}

object ScriptJQ extends ScriptBuilder {
  def build(src:Option[String]):Script = new ScriptSQ()
}

// --- Solidity Query ---------------------------------------------------------------
class ScriptSQ(src0:Option[String] = None) extends Script("sq","solidity-query") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    SolidityResult
      .extractString(input,src)
      .map(r => r.toString)
  }
}

object ScriptSQ extends ScriptBuilder {
  def build(src:Option[String]):Script = new ScriptSQ()
}

// --- Js --------------------------------------------------------------------------
class ScriptJS(inputVarName:String = "input") extends Script("js","javascript") {
  private lazy val engine = new Polyglot("js",PolyglotSandbox.RESTRICTED_THREADED)
  
  override def run(src:String,input:String,data:Map[String,Any]):Try[String] = {
    try {
      val dataInput = data + (inputVarName -> input)
      val r = engine.run(src,dataInput)
      r match {
        case null => Failure(new Exception("result: null"))
        case r => Success(r.toString)
      }
    } catch {
      // case e:jdk.nashorn.internal.runtime.ECMAException => Failure(e)
      // case e:javax.script.ScriptException => Failure(e)
      // case e:Exception => Failure(e)
      case e:Throwable => Failure(e)
    }
  }
}

object ScriptJS extends ScriptBuilder {
  def build(src:Option[String]):Script = new ScriptJS()
}

// --- String --------------------------------------------------------------------------
class ScriptStr extends Script("str","string") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {    
    Success(input)
  }
}

object ScriptStr extends ScriptBuilder {
  val STR = new ScriptStr()
  def build(src:Option[String]):Script = STR
}

// --- String --------------------------------------------------------------------------
class ScriptNone extends Script("none","") {
  def run(src:String,input:String,data:Map[String,Any]):Try[String] = {    
    Success("")
  }
}

object ScriptNone extends ScriptBuilder {
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

object ScriptSleepTest extends ScriptBuilder {
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
        pattern.matches(input).toString()
      }
    }
  }
  
  class ExprNotMatch(expr:String) extends ExprMatch(expr) {
    override def run(input:String,data:Map[String,Any]):Try[String] = {
      Try {
        (! pattern.matches(input)).toString()
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

object ScriptRegexp extends ScriptBuilder {
  def build(src:Option[String]):Script = new ScriptRegexp(src)
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

object ScriptAI extends ScriptBuilder {
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

object ScriptFlow extends ScriptBuilder {
  def build(flow:Seq[Script]):Script = new ScriptFlow(flow)

  def parseUri(uri:String):Option[Script] = {
    if(uri.isBlank()) return None
    uri.split("://").toList match {
      case "jq" :: src :: Nil => Some(new ScriptJQ(Some(src)))
      case "sq" :: src :: Nil => Some(new ScriptSQ(Some(src)))
      case "regexp" :: src :: Nil => Some(new ScriptRegexp(Some(src)))
      case "ai" :: src :: Nil => Some(new ScriptAI(Some(src)))
      case "js" :: src :: Nil => Some(new ScriptJS(src))
      case "str" :: _ => Some(new ScriptStr())
      case src => Some(new ScriptStr())
    }      
  }

  def build(flow:Option[String]):Script = {
    if(flow.isEmpty) return new ScriptFlow(Seq.empty)

    val engines = flow
      .get
      .split(",")
      .filter(! _.isBlank())      
      .flatMap(e => parseUri(e.trim))
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

  private var engines:Map[ID,ScriptBuilder] = Map(
    "" -> ScriptNone,
    "str" -> ScriptStr,
    "js" -> ScriptJS,
    "sq" -> ScriptSQ,
    "regexp" -> ScriptRegexp,
    "jq" -> ScriptJQ,
    "ai" -> ScriptAI,
    "flow" -> ScriptFlow
  )

  def add(id:ID,se:ScriptBuilder):Script.type = {
    engines = engines + (id -> se)
    
    this
  }
  
  def find(id:ID):Option[ScriptBuilder] = {
    engines.get(id)
  }

  def apply(id:ID,src:Option[String]):Try[Script] = {
    find(id.trim) match {
      case Some(builder) => Success(builder.build(src))
      case None => Failure(new Exception(s"Script not found: '${id}'"))
    }
  }

  // resulst as score
  def score(result:Try[String]):Double = {
    result.flatMap( r => {
      Try(r.toDouble)
        .map( s => {
          if(s > 1.0) 1.0
          else 
          if(s < 0.0) 0.0
          else s
        })
    })
    .getOrElse(0.0)
  }
}
