package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

trait Runtime[T] {

  def pluginStart():Try[Any]
  def pluginStop():Try[Any]
  def pluginId():Try[String]

  def pluginName():String = ""
  def pluginVer():String = "0.0.0"
}
