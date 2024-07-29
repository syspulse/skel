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

  def spawn():Seq[Try[Plugin]] = {
    store.all.map( p => 
      spawn(p)
    )
  }

  def spawn(p:PluginDescriptor):Try[Plugin] = {
    p.typ match {
      case "class" | "jar" => 
        new ClassRuntime().spawn(p)

      case _ => Failure(new Exception(s"unknown type: ${p.typ}"))
    }    
  }

  
  def start(r:Plugin):Try[Plugin] = {
    log.info(s"start: ${r}")
    
    // os.makeDir.all(os.Path(wfRuntimeDir,os.pwd))
    // createDataDir(plugin.getId)
    
    r.pluginStart()

    Success(r)
  }

  def stop(r:Plugin):Try[Plugin] = {
    log.info(s"stop: ${r}")

    r.pluginStop()

    Success(r)
  }

}
