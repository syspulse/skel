package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

trait Plugin {

  def pluginStart():Try[Any] = Success(pluginName())
  def pluginStop():Try[Any] = Success(pluginName())
  
  def pluginId():String = this.toString
  def pluginName():String = this.toString
  def pluginVer():String = "0.0.0"
}
