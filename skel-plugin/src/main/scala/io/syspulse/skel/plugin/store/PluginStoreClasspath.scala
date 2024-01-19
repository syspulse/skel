package io.syspulse.skel.plugin.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.jdk.CollectionConverters._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.plugin._

class PluginStoreClasspath(root: Option[Class[_]] = None) extends PluginStoreMem {
    
  override def all:Seq[Plugin] = {    
    val cl = root.getOrElse(this).getClass.getClassLoader
    PluginStoreClasspath.load(cl)
  }
}

object PluginStoreClasspath {
  val log = Logger(s"${this}")

  def load(cl:ClassLoader):Seq[Plugin] = {        
    val pp = cl.getResources("META-INF/MANIFEST.MF").asScala.toSeq.flatMap( url => {
      log.debug(s"${url}")
      // try to load file
      try {
        val manifestFile = new String(url.openStream().readAllBytes())
        if(url.toString().contains("plugin")) {
          log.info(s"${url}")
          log.info(s"${manifestFile}")
        }
        val manifest = manifestFile.split("[\n\r]").filter(!_.isBlank).map( s => s.split(":").toList match {
          case k :: v :: Nil => k -> v.trim
          case k :: Nil => k -> ""
          case _ => "" -> ""
        }).toMap
        
        val title = manifest.get("Plugin-Title").getOrElse("")
        val ver = manifest.get("Plugin-Version").getOrElse("")
        val init = manifest.get("Plugin-Class").getOrElse("")

        log.info(s"${url}: ${title}:${ver}: class=${init}")
        val plugin = if(init != "") 
          Some(Plugin(name = title,typ = "jar", init = init, ver = ver))
        else
          None
        
        plugin
      } catch {
        case e:Exception => 
          log.warn(s"failed to load resource: ${url}",e)
          None
      }      
    })

    pp
  }
}

