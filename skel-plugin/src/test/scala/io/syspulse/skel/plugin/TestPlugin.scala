package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class TestPlugin(p:Plugin) extends Runtime[String]() {
  
  var err = 0

  def start():Try[Any] = Success("OK")
  def stop():Try[Any] = Success("OK")

  def id():Try[String] = Success(this.toString)
}
