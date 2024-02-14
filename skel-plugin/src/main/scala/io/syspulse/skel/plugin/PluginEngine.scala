package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.ActorSystem
import akka.actor.typed.SupervisorStrategy

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin.store._

class PluginEngine(store:PluginStore) {
  val log = Logger(s"${this}")

  def spawn():Seq[Try[Runtime[_]]] = {
    store.all.map( p => 
      spawn(p)
    )
  }

  def spawn(p:Plugin):Try[Runtime[_]] = {
    p.typ match {
      case "class" | "jar" => 
        new ClassRuntime().spawn(p)

      case _ => Failure(new Exception(s"unknown type: ${p.typ}"))
    }    
  }

  
  def start(r:Runtime[_]):Try[Runtime[_]] = {
    log.info(s"start: ${r}")
    
    // os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))
    // createDataDir(plugin.getId)
    
    r.start()

    Success(r)
  }

  def stop(r:Runtime[_]):Try[Runtime[_]] = {
    log.info(s"stop: ${r}")

    r.stop()

    Success(r)
  }

}
