package io.syspulse.skel.dsl

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import java.util.function.Predicate
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.TimeoutException

// ATTENTION: with 
// Multi threaded access requested by thread Thread[#162,pool-58-thread-1,5,main] but is not allowed for language(s) js.

/*
allowHostAccess

EXPLICIT - Java host methods or fields, must be public and be annotated with @Export to make them accessible to the guest language.
SCOPED - Java host methods or fields, must be public and be annotated with @Export to make them accessible to the guest language. Guest-to-host callback parameter validity is scoped to the duration of the callback by default.
NONE - Does not allow any access to methods or fields of host objects. Java host objects may still be passed into a context, but they cannot be accessed.
ALL - Does allow full unrestricted access to public methods or fields of host objects. Note that this policy allows unrestricted access to reflection. It is highly discouraged from using this policy in environments where the guest application is not fully trusted.
CONSTRAINED host access policy suitable for a context with CONSTRAINED sandbox policy.
ISOLATED host access policy suitable for a context with ISOLATED sandbox policy.
UNTRUSTED host access policy suitable for a context with UNTRUSTED sandbox policy.

*/

/*
sandbox

TRUSTED - The sandbox allows full access to all Java APIs.
CONSTRAINED - The sandbox allows access to most Java APIs, but restricts some APIs that are potentially dangerous or have a high risk of abuse.
ISOLATED - The sandbox isolates the guest language from the host environment, allowing only limited access to Java APIs.
UNTRUSTED - The sandbox does not allow any access to Java APIs.
*/

/* 
current Truffle runtime only supports the TRUSTED or CONSTRAINED sandbox policies. 
This typically occurs when a non-Oracle GraalVM Java runtime is used, the org.graalvm.truffle:truffle-enterprise dependency is missing, 
or the fallback runtime was forced. The Truffle fallback runtime may be forced using the truffle.UseFallbackRuntime or 
truffle.TruffleRuntime system property. To resolve this make sure Oracle GraalVM is used, the truffle-enterprise dependency is on 
the class or module path and the fallback runtime is not forced. Alternatively, you can switch to a less strict sandbox policy 
using Builder.sandbox(SandboxPolicy).
 */

object PolyglotSandbox {
  
  val RESTRICTED = Map(
    "allowAllAccess"->false,
    "allowPolyglotAccess" -> "NONE",
    "allowIO" -> false,
    "allowCreateProcess" -> false,
    "allowCreateThread" -> false,
    "allowEnvironmentAccess"->"NONE",
    "allowHostAccess"-> "CONSTRAINED",
    "allowHostClassLoading" -> false,
    "allowHostClassLookup"->"java.lang.,java.math.,java.util.",

    "sandbox"->"CONSTRAINED",
    "out" -> 1024 * 10,
    "err" -> 1024 * 10,
    "option"-> Seq(               
               "engine.SpawnIsolate:true",
               "engine.MaxIsolateMemory:100m",
               "sandbox.MaxHeapMemory:100MB",
               "sandbox.MaxCPUTime:5s",
               "sandbox.MaxStatements:10000",
               "sandbox.MaxASTDepth:100",
               "sandbox.MaxStackFrames:10",
               "sandbox.MaxThreads:1",
               "sandbox.MaxOutputStreamSize:10KB",
               "sandbox.MaxErrorStreamSize:10KB")
               .mkString("|")
  )

  val RESTRICTED_1 = Map(
    "allowAllAccess"->false,
    "allowPolyglotAccess" -> "NONE",
    "allowIO" -> false,
    "allowCreateProcess" -> false,
    "allowCreateThread" -> false,
    "allowEnvironmentAccess"->"NONE",
    "allowHostAccess"-> "UNTRUSTED",
    "allowHostClassLoading" -> false,
    "allowHostClassLookup"->"java.lang.,java.math.,java.util.",

    "sandbox"->"UNTRUSTED",
    "option"-> Seq(
               "engine.SpawnIsolate:true",
               "engine.MaxIsolateMemory:100m",
               "sandbox.MaxHeapMemory:100MB",
               "sandbox.MaxCPUTime:5s",
               "sandbox.MaxASTDepth:100",
               "sandbox.MaxStackFrames:10",
               "sandbox.MaxThreads:1",
               "sandbox.MaxOutputStreamSize:10KB",
               "sandbox.MaxErrorStreamSize:10KB")
               .mkString("|")
  )

  val RESTRICTED_THREADED = Map(
    "allowAllAccess"->false,
    "allowPolyglotAccess"->"NONE",
    "allowIO"->false,
    "allowCreateProcess"->false,
    "allowCreateThread"-> true,
    "allowEnvironmentAccess"->"NONE",
    "allowHostAccess"-> "UNTRUSTED",
    "allowHostClassLoading"->true,
    "allowHostClassLookup"->"java.lang.,java.math.,java.util."
  )
}

class PolyglotSandbox(lang:String) extends Polyglot(lang, opt = PolyglotSandbox.RESTRICTED)
{}

object Polyglot {
  val DEF_TIMEOUT = 5000L
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
}

class Polyglot(lang:String,opt:Map[String,Any] = Map(),src0:Option[String] = None, polyLogs:Boolean = false) extends ScriptEngine(lang) {
  log.info(s"[${lang}] opt=${opt}")

  if(!polyLogs) {
    sys.props("polyglot.engine.WarnInterpreterOnly") = "false"
  }

  val timeout = opt.get("timeout").map(_.asInstanceOf[Long]).getOrElse(Polyglot.DEF_TIMEOUT)

  def mapHostAccess(v:String):HostAccess = {
    v.trim.toUpperCase match {
      case "NONE" => HostAccess.NONE
      case "ALL" => HostAccess.ALL
      case "CONSTRAINED" => HostAccess.CONSTRAINED      
      case "ISOLATED" => HostAccess.ISOLATED
      case "SCOPED" => HostAccess.SCOPED
      case "UNTRUSTED" => HostAccess.UNTRUSTED
      case _ => HostAccess.ALL
    }
  }

  def mapPolyglotAccess(v:String):PolyglotAccess = {
    v.trim.toUpperCase match {
      case "NONE" => PolyglotAccess.NONE
      case "ALL" => PolyglotAccess.ALL
      case _ => PolyglotAccess.NONE
    }
  }

  def mapHostClassLookup(v:String): Predicate[String] = {
    val entries = v.split(",").map(_.trim).filter(_.nonEmpty)
    val rules = entries.flatMap { entry =>
      entry.split("=", 2).toList match {
        case name :: value :: Nil if name.nonEmpty =>
          val allowed = value.trim.toLowerCase match {
            case "" => true
            case "true" => true
            case "false" => false
            case other =>
              log.warn(s"Unknown allowHostClassLookup value '$other' for class '$name', defaulting to true")
              true
          }
          Some(name -> allowed)
        case name :: Nil if name.nonEmpty =>
          Some(name -> true)
        case _ =>
          None
      }
    }.toMap

    new Predicate[String] {
      override def test(className: String): Boolean = {
        if (className == null) {
          false
        } else {
          rules.getOrElse(className, true)
        }
      }
    }
  }

  def mapSandbox(v:String):SandboxPolicy = {
    v.trim.toUpperCase match {
      case "TRUSTED" => SandboxPolicy.TRUSTED
      case "CONSTRAINED" => SandboxPolicy.CONSTRAINED
      case "ISOLATED" => SandboxPolicy.ISOLATED
      case "UNTRUSTED" => SandboxPolicy.UNTRUSTED      
    }
  }

  def mapOption(ctx:Context#Builder,v:String):Context#Builder = {
    v.split("|").map(_.trim).filter(_.nonEmpty).flatMap { entry =>
      entry.split(":", 2).toList match {
        case key :: value :: Nil if key.nonEmpty => Some(key -> value)
        case _ => None
      }
    }.foldLeft(ctx) { case (ctx, (key,value)) =>
      ctx.option(key,value.asInstanceOf[String])
    }    
  }

  val ctx = {
    val ctx = for {
      ctx <- Try( Context.newBuilder(lang) )

      ctx <- Try(opt.get("sandbox").map(v => ctx.sandbox(mapSandbox(v.asInstanceOf[String]))).getOrElse(ctx))

      ctx <- Try(opt.get("option").map(v => mapOption(ctx,v.asInstanceOf[String])).getOrElse(ctx))

      ctx <- Try(opt.get("out").map(v => ctx.out(new ByteArrayOutputStream(v.asInstanceOf[Int]))).getOrElse(ctx))      
      ctx <- Try(opt.get("err").map(v => ctx.err(new ByteArrayOutputStream(v.asInstanceOf[Int]))).getOrElse(ctx))      

      ctx <- Try(opt.get("allowAllAccess").map(v => ctx.allowAllAccess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowNativeAccess").map(v => ctx.allowNativeAccess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowHostAccess").map(v => ctx.allowHostAccess(mapHostAccess(v.asInstanceOf[String]))).getOrElse(ctx))
      ctx <- Try(opt.get("allowPolyglotAccess").map(v => ctx.allowPolyglotAccess(mapPolyglotAccess(v.asInstanceOf[String]))).getOrElse(ctx))
      
      ctx <- Try(opt.get("allowIO").map(v => ctx.allowIO(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowCreateProcess").map(v => ctx.allowCreateProcess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowCreateThread").map(v => ctx.allowCreateThread(v.asInstanceOf[Boolean])).getOrElse(ctx))

      ctx <- Try(opt.get("allowEnvironmentAccess").map(v => v match {
        case Some("NONE") => ctx.allowEnvironmentAccess(EnvironmentAccess.NONE)
        case Some("INHERIT") => ctx.allowEnvironmentAccess(EnvironmentAccess.INHERIT)      
        case _ => ctx.allowEnvironmentAccess(EnvironmentAccess.NONE)      
      }).getOrElse(ctx))      
      
      ctx <- Try(opt.get("allowHostClassLoading").map(v => ctx.allowHostClassLoading(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowHostClassLookup").map {
        case predicate: Predicate[_] =>
          ctx.allowHostClassLookup(predicate.asInstanceOf[Predicate[String]])
        case v: String =>
          ctx.allowHostClassLookup(mapHostClassLookup(v))
        case other =>
          log.warn(s"Unsupported allowHostClassLookup value: ${other}")
          ctx
      }.getOrElse(ctx))

    } yield ctx.build()
    
    ctx match {
      case Success(ctx) =>        
        ctx
      case Failure(e) =>
        log.error(s"[${lang}] failed to create context: ${e.getMessage()}")
        throw e
    }
  }

  log.info(s"[${lang}] ctx=${ctx}")

  // Try to precompile src0 if it's a function, otherwise store as string
  // If precompilation fails (e.g., references undefined variables), store as string for runtime evaluation
  val (func0: Option[Value], src0Script: Option[String]) = src0 match {
    case Some(src) =>
      Try(ctx.eval(lang, src)) match {
        case Success(v) if v.canExecute() => 
          // It's a function that can be executed - precompile it
          (Some(v), None)
        case Success(v) => 
          // It's a value (not a function) - precompile it
          (Some(v), None)
        case Failure(_) => 
          // Can't precompile (likely references undefined variables) - store as string for runtime evaluation
          (None, Some(src))
      }
    case None => (None, None)
  }

  log.info(s"[${lang}] func0=${func0}, src_scrip0=${src0Script}")

  def run(script:String,args:Map[String,Any] = Map()):Try[Any] = {    

    log.info(s"[${lang}] ${ctx}: args=${args}, script=${script} (src0=${src0})")

    if(script.isBlank && func0.isEmpty && src0Script.isEmpty) {
      return Failure(new Exception("No Script specified"))
    }

    Try {
      // clear previous bindings - only remove ones we can safely remove
      val bindings = ctx.getBindings(lang)
      if (bindings.hasMembers) {
        bindings.getMemberKeys.asScala
        .filter(key => args.contains(key))
        .foreach { key =>
          try {
            bindings.removeMember(key)
          } catch {
            case _: Exception => // Ignore errors when removing bindings
          }
        }
      }

      // set new bindings with proper proxy handling for case classes
      args.foreach { case (k,v) =>
        val proxyValue = v match {
          case cc: Product => createCaseClassProxy(cc)
          case other => other
        }
        ctx.getBindings(lang).putMember(k, proxyValue)
      }

      import Polyglot.ec
      // Determine which script is being used for logging/error messages
      val scriptForLogging = if(!script.isBlank) {
        script
      } else if(func0.isDefined) {
        s"<precompiled:${src0.getOrElse("")}>"
      } else {
        src0Script.getOrElse("")
      }
      
      val executionFuture = Future {
        val func = {
          if(!script.isBlank) {
            // Use provided script
            ctx.eval(lang, script)
          } else if(func0.isDefined) {
            // Use precompiled function/value
            func0.get
          } else {
            // Evaluate src0Script with current bindings
            ctx.eval(lang, src0Script.get)
          }
        }
        val result = if(func.canExecute()) {
          func.execute(args)
        } else {
          func
        }
        result
      }

      try {
        Await.result(executionFuture, FiniteDuration(timeout, TimeUnit.MILLISECONDS))      
      } catch {
        case e: TimeoutException =>
          log.warn(s"Execution timed out: '${scriptForLogging}': ${timeout}ms: ctx=${ctx}",e)
          //ctx.close(true)
          ctx.interrupt(java.time.Duration.ofMillis(timeout))        
          throw e
        case e: PolyglotException =>
          log.warn(s"Execution failed: '${scriptForLogging}'",e)
          throw e
      } finally {
        //Try(ctx.close())
      }
    }
  }

  private def createCaseClassProxy(cc: Product): ProxyObject = {
    new ProxyObject {
      override def getMember(key: String): AnyRef = {
        val fieldIndex = cc.productElementNames.indexOf(key)
        if (fieldIndex >= 0) {
          cc.productElement(fieldIndex).asInstanceOf[AnyRef]
        } else {
          null
        }
      }

      override def getMemberKeys(): Array[String] = {
        cc.productElementNames.toArray
      }

      override def hasMember(key: String): Boolean = {
        cc.productElementNames.contains(key)
      }

      override def putMember(key: String, value: Value): Unit = {
        // Read-only access - do nothing
      }

      override def removeMember(key: String): Boolean = {
        // Read-only access - return false
        false
      }
    }
  }
}