package io.syspulse.skel.service.config

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.config.ConfigRegistry._

import spray.json.DefaultJsonProtocol

import java.util.{UUID}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

object ConfigJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val configJsonFormat = jsonFormat2(Config)
  implicit val configsJsonFormat = jsonFormat1(Configs)
  
}
