package io.syspulse.skel.dsl

import scala.jdk.CollectionConverters._
import org.graalvm.polyglot.Context
import org.graalvm.polyglot._
import org.graalvm.polyglot.proxy._

class Polyglot(lang:String) extends ScriptEngine(lang) {
  val ctx = Context.newBuilder(lang).build()

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