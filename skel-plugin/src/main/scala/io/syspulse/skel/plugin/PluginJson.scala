package io.syspulse.skel.plugin

import io.syspulse.skel.service.JsonCommon

import spray.json._
import DefaultJsonProtocol._

object PluginJson extends JsonCommon with NullOptions {

  implicit val jf_pl_pl = jsonFormat5(PluginDescriptor.apply _)  
}
