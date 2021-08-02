package io.syspulse.skel.service.health

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.health.HealthRegistry._

import spray.json.DefaultJsonProtocol

import java.util.{UUID}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

object HealthJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val healthJsonFormat = jsonFormat1(Health)
  
}
