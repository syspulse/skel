package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._
import io.syspulse.skel.plugin._

class TestPlugin_2(p:PluginDescriptor) extends Plugin() {
    
  override def pluginId():String = "2"
}
