package io.syspulse.skel.ai.server

import io.syspulse.skel.service.JsonCommon
import spray.json._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

import io.syspulse.skel.ai._
import io.syspulse.skel.ai.server._

object AiJson extends JsonCommon {
  
  implicit val jf_ai = jsonFormat7(Ai)
  implicit val jf_ais = jsonFormat2(Ais)
  
  implicit val jf_ai_cr = jsonFormat3(AiCreateReq)
  implicit val jf_ai_res = jsonFormat2(AiRes)    
    
}
