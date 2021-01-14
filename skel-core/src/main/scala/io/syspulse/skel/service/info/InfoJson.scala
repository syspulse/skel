package io.syspulse.skel.service.info

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.info.InfoRegistry._

import spray.json.DefaultJsonProtocol

import java.util.{UUID}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

object InfoJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val varJsonFormat = jsonFormat2(Var)
  implicit val envJsonFormat = jsonFormat1(Environment)
  implicit val jvmJsonFormat = jsonFormat2(Jvm)
  implicit val healthJsonFormat = jsonFormat1(Health)
  implicit val infoJsonFormat = jsonFormat5(Info)
  
}
