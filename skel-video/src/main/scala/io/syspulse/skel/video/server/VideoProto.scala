package io.syspulse.skel.video.server

import scala.collection.immutable
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.VideoJson
import io.syspulse.skel.service.JsonCommon

final case class Videos(videos: immutable.Seq[Video])

final case class VideoCreateReq(title:String)
final case class VideoRandomReq()
final case class VideoActionRes(status: String,id:Option[String])
final case class VideoRes(video: Option[Video])

object VideoProto extends JsonCommon {
  
  import DefaultJsonProtocol._

  import VideoJson._

  implicit val jf_Videos = jsonFormat1(Videos)
  implicit val jf_VideoRes = jsonFormat1(VideoRes)
  implicit val jf_CreateReq = jsonFormat1(VideoCreateReq)
  implicit val jf_ActionRes = jsonFormat2(VideoActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(VideoRandomReq)
  
}