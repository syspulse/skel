package io.syspulse.skel.plugin.runtime

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.Plugin
import io.syspulse.skel.plugin.Runtime

trait PluginRuntime {
  def spawn(plugin:Plugin):Try[Runtime[_]]
}