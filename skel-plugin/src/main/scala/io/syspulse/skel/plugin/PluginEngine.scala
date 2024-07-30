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

object PluginEngine {
  val log = Logger(s"${this}")

  def apply(uri:String) = {
    val store = uri.split("://").toList match {
      case ("classpath" | "class") :: className :: Nil =>  new PluginStoreClasspath(className)
      case "dir" :: Nil =>  new PluginStoreDir()
      case "dir" :: dir :: Nil => new PluginStoreDir(dir)
      
      case "manifest" :: dir :: Nil => new PluginStoreManifest(dir)
      case "manifest" :: Nil => new PluginStoreManifest()
      
      case ("jars" | "jar") :: mask :: Nil => new PluginStoreJar(classMask = mask)
      case ("jars" | "jar") :: dir :: mask :: Nil => new PluginStoreJar(dir,classMask = mask)

      case className :: _ => new PluginStoreClasspath(className)
      case _ => new PluginStoreMem()
    }

    new PluginEngine(store)
  }

  def run(uri:String) = {
    val pe = apply(uri)
    val n = pe.load()
    val pp = pe.start()
    log.info(s"running: ${pp}")
    pe
  }
}

class PluginEngine(store:PluginStore) {
  val log = Logger(s"${this}")

  // cache if start()/stop() is used
  var cache:Seq[Plugin] = Seq()

  def all[T]():Seq[T] = cache.asInstanceOf[Seq[T]]
  
  def load() = {
    store.loadPlugins()
  }

  def spawn():Seq[Try[Plugin]] = {
    store.all.map( p => 
      spawn(p)
    )
  }

  def spawn(p:PluginDescriptor):Try[Plugin] = {
    p.typ match {
      case "class" | "jar" => 
        new ClassRuntime().spawn(p)

      case _ => Failure(new Exception(s"unknown Plugin type: ${p.typ}"))
    }    
  }

  // start all plugns
  def start():Seq[Plugin] = {
    spawn().flatMap(plugin => plugin match {
      case Success(p) => 
        cache = cache :+ p
        Some(p)
      case Failure(e) =>
        log.error(s"failed to start plugin: ${plugin}",e)
        None
    })
  }

  // stop all plugins
  def stop():Int = {
    cache.map( p => stop(p))
    val sz = cache.size
    cache = Seq()
    sz
  }
  
  def start(id:String):Try[Plugin] = {
    log.info(s"start: ${id}")
        
    for {
      plugin <- store.?(id)  
      r <- spawn(plugin)       
    } yield r    
  }
  
  def start(r:Plugin):Try[Plugin] = {
    log.info(s"start: ${r}")
    
    r.pluginStart()

    Success(r)
  }

  def stop(r:Plugin):Try[Plugin] = {
    log.info(s"stop: ${r}")

    r.pluginStop()

    Success(r)
  }

}
