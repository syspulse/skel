package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class TestPlugin_2(p:Plugin) extends Runtime[String]() {
    
  def start():Try[Any] = Success("2")
  def stop():Try[Any] = Success("2")

  def id():Try[String] = Success(this.toString)
}
