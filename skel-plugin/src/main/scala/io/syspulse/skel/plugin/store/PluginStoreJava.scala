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

import java.net.URLClassLoader

import io.syspulse.skel.plugin._
import java.util.regex.Pattern
import java.net.JarURLConnection
import java.util.jar.JarFile
import java.net.URL

object PluginStoreJava {
  val log = Logger(s"${this}")

  def loadFromJars(cl:URLClassLoader,classMask:Option[String]):Seq[Plugin] = {
    log.info(s"cl=${cl}, mask=${classMask}")

    val pp = cl.getURLs().toArray.flatMap( url => {
      log.info(s"${url}")
      // try to load jar file
      //val urlJar:JarURLConnection = url.openConnection().asInstanceOf[JarURLConnection]
      val urlJar = new URL("jar:" + url.toString() + "!/")      
      val jarConn:JarURLConnection = urlJar.openConnection().asInstanceOf[JarURLConnection]
      
      try {
        val jar:JarFile = jarConn.getJarFile()
        
        val ee = jar.entries().asScala.toList
        log.info(s"${jar}: ${ee.size}")
        
        ee
          .filter( e => {
            log.debug(s"${e}")
            classMask.isDefined && e.getName.matches(classMask.get)
          })
          .map( e => {
            val name = e.toString.split("/").last.replace(".class","")
            val initClass = e.toString.replaceAll("/",".").replace(".class","")
            log.info(s"${e}: name=${name}: class=${initClass}")
        
            Plugin(name = name,typ = "jar", init = initClass, ver = "")
          })
                
      } catch {
        case e:Exception => 
          log.warn(s"failed to load resource: ${url}",e)
          Seq()
      }
    })

    pp
  }

  def loadFromManifest(cl:ClassLoader):Seq[Plugin] = {        
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

        log.debug(s"${url}: ${title}:${ver}: class=${init}")
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

