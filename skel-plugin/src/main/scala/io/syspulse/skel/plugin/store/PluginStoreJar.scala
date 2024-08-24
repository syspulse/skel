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

class PluginStoreJar(dir:String = "plugins",classMask:String) extends PluginStoreDir(dir) {
  
  override def scan():Seq[PluginDescriptor] = {
    
    val storeDir = os.Path(dir,os.pwd)
    
    log.info(s"Scanning dir: ${storeDir}")

    val parent = this.getClass().getClassLoader()
    val cc = os.walk(storeDir)
      .filter(_.toIO.isFile())
      .sortBy(_.toIO.lastModified())
      .flatMap(f => {
        log.info(s"Loading file: ${f}")
        val child:URLClassLoader = new URLClassLoader(Array[URL](new URL(s"file://${f}")), parent)

        log.debug(s"child=${child}, parent=${parent}")
        
        PluginStoreJava.loadFromJars(child,Some(classMask))
      })

    cc    
  }

  override def loadPlugins():Int = {
    val pp = scan()
    pp.foreach{ p => store.+(p)}
    all.size
  }

}