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

class PluginStoreClasspath(classNames0:String,root: Option[Class[_]] = None) extends PluginStoreMem {

  def scan() = {
    val cl = root.getOrElse(this).getClass.getClassLoader
    val classNames = classNames0.split(",").map(_.trim).filter(! _.isBlank).toSeq
    PluginStoreJava.loadFromClasspath(cl,classNames)        
  } 

  override def loadPlugins():Int = {
    val pp = scan()
    pp.foreach{p => this.+(p)}
    all.size
  }
}
