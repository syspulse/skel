package io.syspulse.skel.dash.server

import io.syspulse.skel.service.JsonCommon
import spray.json._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport

import io.syspulse.skel.dash.Dash

object DashJson extends JsonCommon {
  implicit val jf_dash_data = jsonFormat6(DashData)
  implicit val jf_dash_data_req = jsonFormat7(DashDataReq)

  // Custom formatter for Dash that maps "desc" JSON field to "info" field
  implicit val jf_dash: RootJsonFormat[Dash] = new RootJsonFormat[Dash] {
    def write(dash: Dash): JsValue = JsObject(
      "id" -> JsString(dash.id),
      "layout" -> JsString(dash.layout),
      "name" -> dash.name.toJson,
      "desc" -> dash.info.toJson,  // Map info field to "desc" in JSON
      "tags" -> dash.tags.toJson,
      "pid" -> dash.pid.toJson,
      "tid" -> dash.tid.toJson,
      "ts" -> JsNumber(dash.ts),
      "ts0" -> JsNumber(dash.ts0),
      "status" -> dash.status.toJson
    )
    
    def read(json: JsValue): Dash = {
      val fields = json.asJsObject.fields
      Dash(
        id = fields("id").convertTo[String],
        layout = fields("layout").convertTo[String],
        name = fields.get("name").filter(_ != JsNull).map(_.convertTo[String]),
        info = fields.get("desc").filter(_ != JsNull).map(_.convertTo[String]),  // Map "desc" JSON field to info
        tags = fields.get("tags").filter(_ != JsNull).map(_.convertTo[Vector[String]]),
        pid = fields.get("pid").filter(_ != JsNull).map(_.convertTo[String]),
        tid = fields.get("tid").filter(_ != JsNull).map(_.convertTo[String]),
        ts = fields("ts").convertTo[Long],
        ts0 = fields("ts0").convertTo[Long],
        status = fields.get("status").filter(_ != JsNull).map(_.convertTo[Int])
      )
    }
  }

  implicit val jf_dash_layout = jsonFormat9(DashLayout)
  implicit val jf_dashs = jsonFormat2(Dashs)
  implicit val jf_dash_create = jsonFormat6(DashCreateReq)  
  implicit val jf_dash_update = jsonFormat7(DashUpdateReq)
  implicit val jf_dash_res = jsonFormat1(DashRes)
}
