package io.syspulse.skel.plugin

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin._

trait Sentry {
  protected val log = Logger(s"${this}")
  def did:String

  def onBlock(b:Int) = {}
}

abstract class DetectorAbstract(id:String = "") {
  def getId() = id
}

class DetectorExtBlock(p:PluginDescriptor) extends DetectorAbstract with Plugin with Sentry {
  override def did = p.name.replaceAll("%20"," ")
  override def toString = s"${this.getClass.getSimpleName}(${did})"
  
  override def onBlock(b:Int) = {
    log.info(s"DetectorBlock(): onBlock: ${b}")
    Seq()
  }

  //def pluginId():String = did
}

class DetectorExtTx(p:PluginDescriptor) extends DetectorAbstract with Plugin with Sentry {
  override def did = p.name.replaceAll("%20"," ")
  override def toString = s"${this.getClass.getSimpleName}(${did})"
  
  //def pluginId():String = did
}

