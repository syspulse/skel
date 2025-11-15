package io.syspulse.skel.dsl

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import org.graalvm.polyglot.Context
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._
import java.util.function.Predicate

class PolyglotSandbox(lang:String) extends Polyglot(lang,
  opt = Map(
    "allowAllAccess"->false,
    "allowPolyglotAccess"->"NONE",
    "allowIO"->false,
    "allowCreateProcess"->false,
    "allowCreateThread"->false,
    "allowEnvironmentAccess"->"NONE",
    "allowHostAccess"->"ALL",
    "allowHostClassLoading"->true,
    "allowHostClassLookup"->"java.lang.,java.math.,java.util."
  ))
{}


class Polyglot(lang:String,opt:Map[String,Any] = Map()) extends ScriptEngine(lang) {
  log.info(s"[${lang}] opt=${opt}")

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

  val ctx = {
    val ctx = for {
      ctx <- Try( Context.newBuilder(lang) )
      ctx <- Try(opt.get("allowAllAccess").map(v => ctx.allowAllAccess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowNativeAccess").map(v => ctx.allowNativeAccess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowHostAccess").map(v => ctx.allowHostAccess(mapHostAccess(v.asInstanceOf[String]))).getOrElse(ctx))
      ctx <- Try(opt.get("allowPolyglotAccess").map(v => ctx.allowPolyglotAccess(mapPolyglotAccess(v.asInstanceOf[String]))).getOrElse(ctx))
      
      ctx <- Try(opt.get("allowIO").map(v => ctx.allowIO(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowCreateProcess").map(v => ctx.allowCreateProcess(v.asInstanceOf[Boolean])).getOrElse(ctx))
      ctx <- Try(opt.get("allowCreateThread").map(v => ctx.allowCreateThread(v.asInstanceOf[Boolean])).getOrElse(ctx))

      ctx <- Try(opt.get("allowEnvironmentAccess").map(v => v match {
        case Some("none") => ctx.allowEnvironmentAccess(EnvironmentAccess.NONE)
        case _ => ctx.allowEnvironmentAccess(EnvironmentAccess.INHERIT)      
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
    ctx.get
  }

  def run(script:String,args:Map[String,Any] = Map()):Any = {
    log.info(s"[${lang}] ${ctx}: args=${args}, script=${script}")

    // clear previous bindings - only remove ones we can safely remove
    val bindings = ctx.getBindings(lang)
    if (bindings.hasMembers) {
      bindings.getMemberKeys.asScala.foreach { key =>
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

    val func = ctx.eval(lang, script)

    val result = if(func.canExecute()) {
      func.execute(args)
    } else {
      func
    }
    result
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