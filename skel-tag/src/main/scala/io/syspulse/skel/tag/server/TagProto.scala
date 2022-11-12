package io.syspulse.skel.tag.server

import scala.collection.immutable
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.tag.Tag
import io.syspulse.skel.tag.TagJson

final case class Tags(tags: immutable.Seq[Tag])

final case class TagCreateReq(id:String,tags:List[String])
final case class TagRandomReq()
final case class TagActionRes(status: String,id:Option[String])
final case class TagRes(tag: Option[Tag])

object TagProto extends JsonCommon {
  
  import DefaultJsonProtocol._

  import TagJson._

  implicit val jf_Tags = jsonFormat1(Tags)
  implicit val jf_TagRes = jsonFormat1(TagRes)
  implicit val jf_CreateReq = jsonFormat2(TagCreateReq)
  implicit val jf_ActionRes = jsonFormat2(TagActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(TagRandomReq)
  
}