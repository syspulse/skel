package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class TestPlugin_1(p:Plugin) extends Runtime[String]() {
  
  var err = 0

  def start():Try[Any] = Success("1")
  def stop():Try[Any] = Success("1")

  def id():Try[String] = Success(this.toString)
}
