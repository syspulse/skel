package io.syspulse.skel.notify.client

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.settings.ClientConnectionSettings

//import spray.json.DefaultJsonProtocol._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.NotUsed

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import io.jvm.uuid._

import io.syspulse.skel.ClientHttp
import io.syspulse.skel.util.Util
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.notify._
import io.syspulse.skel.notify.server.NotifyJson


class NotifyClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends ClientHttp[NotifyClientHttp](uri)(as,ec) with NotifyService {
  
  import NotifyJson._
  import spray.json._
  
  def reqPostNotify(to:String,subj:String,msg:String,severity:Option[Int],scope:Option[String]) =  
    HttpRequest(method = HttpMethods.POST, uri = s"${uri}", headers=authHeaders(),
      entity = HttpEntity(ContentTypes.`application/json`, 
        NotifyReq(Some(to),Some(subj),msg,severity,scope).toJson.toString)
    )
  
  def notify(to:String,subj:String,msg:String,severity:Option[Int],scope:Option[String]):Future[Option[Notify]] = {
    val req = reqPostNotify(to,subj,msg,severity,scope)
    log.info(s"-> ${req}")
    for {
      rsp <- Http().singleRequest(req)
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Notify]] else Future(None)
    } yield r
  }

}

object NotifyClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "NotifyClientHttp")
  implicit val ec = system.executionContext

  def apply(uri:String):NotifyClientHttp = {
    new NotifyClientHttp(uri)
  }
}
