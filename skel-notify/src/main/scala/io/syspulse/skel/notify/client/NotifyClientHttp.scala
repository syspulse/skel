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
  
  def reqGetNotify(id:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/${id}")
  def reqGetNotifyByEid(xid:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/xid/${xid}")
  def reqGetNotifys() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}")
  def reqPostNotify(email:String,name:String,xid:String) =  HttpRequest(method = HttpMethods.POST, uri = s"${uri}",
        entity = HttpEntity(ContentTypes.`application/json`, 
          NotifyCreateReq(email,name,xid).toJson.toString)
      )
  def reqDeleteNotify(id:UUID) = HttpRequest(method = HttpMethods.DELETE, uri = s"${uri}/${id}")

  def delete(id:UUID):Future[NotifyActionRes] = {
    log.info(s"${id} -> ${reqDeleteNotify(id)}")
    for {
      rsp <- Http().singleRequest(reqDeleteNotify(id))
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[NotifyActionRes] else Future(NotifyActionRes(status=s"${rsp.status}: ${rsp.entity}",None))
    } yield r 
  }

  def create(email:String,name:String,xid:String):Future[Option[Notify]] = {
    log.info(s"-> ${reqPostNotify(email,name,xid)}")
    for {
      rsp <- Http().singleRequest(reqPostNotify(email,name,xid))
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Notify]] else Future(None)
    } yield r
  }

  def get(id:UUID):Future[Option[Notify]] = {
    log.info(s"${id} -> ${reqGetNotify(id)}")
    for {
      rsp <- Http().singleRequest(reqGetNotify(id))
      notify <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Notify]] else Future(None)
    } yield notify 
  }

  def getByEid(xid:String):Future[Option[Notify]] = {
    log.info(s"${xid} -> ${reqGetNotifyByEid(xid)}")    
    for {
      rsp <- Http().singleRequest(reqGetNotifyByEid(xid))        
      notify <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Notify]] else Future(None)
    } yield notify
  }

  def getByEidAlways(xid:String):Future[Option[Notify]] = {
    log.info(s"${xid} -> ${reqGetNotifyByEid(xid)}")    
    
    for {
      rsp <- Http().singleRequest(reqGetNotifyByEid(xid),
                                  settings = ConnectionPoolSettings(NotifyClientHttp.system)
                                    .withConnectionSettings(ClientConnectionSettings(NotifyClientHttp.system)
                                    .withConnectingTimeout(FiniteDuration(3,"seconds"))))
      .transform {
        case Failure(e) => {
          log.error(s"Failed to call -> ${reqGetNotifyByEid(xid)}: ${e.getMessage()}")
          Try(HttpResponse(StatusCodes.InternalServerError))
        }
        case Success(r) => {
          Try(r)
        }
      }
      notify <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Notify]] else Future(None)
    } yield notify
  }

  def findByEmail(email:String):Future[Option[Notify]] = {
    Future.failed(new NotImplementedError(s""))
  }

  def all():Future[Notifys] = {
    log.info(s" -> ${reqGetNotifys()}")
    for {
      rsp <- Http().singleRequest(reqGetNotifys())
      body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield body.utf8String.parseJson.convertTo[Notifys]
  }
}

object NotifyClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "NotifyClientHttp")
  implicit val ec = system.executionContext

  def apply(uri:String):NotifyClientHttp = {
    new NotifyClientHttp(uri)
  }
}
