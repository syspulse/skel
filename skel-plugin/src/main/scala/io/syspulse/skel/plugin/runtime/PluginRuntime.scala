package io.syspulse.skel.plugin.runtime

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.PluginDescriptor
import io.syspulse.skel.plugin.Plugin

trait PluginRuntime {
  def spawn(plugin:PluginDescriptor):Try[Plugin]
}