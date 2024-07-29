package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class TestPlugin_1(p:Plugin) extends Runtime[String]() {
  
  var err = 0

  def pluginStart():Try[Any] = Success("1")
  def pluginStop():Try[Any] = Success("1")
  def pluginId():Try[String] = Success(this.toString)

  override def pluginName():String = "Test-1"
  override def pluginVer():String = "0.0.1"
}
