package io.syspulse.skel.otp.client

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString

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
import io.syspulse.skel.otp._
import io.syspulse.skel.otp.server.OtpJson

class OtpClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends ClientHttp[OtpClientHttp](uri)(as,ec) {
  
  import OtpJson._
  import spray.json._
  
  def reqGetOtpForUser(userId:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/user/${userId}")
  def reqGetOtp(id:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/${id}")
  def reqGetOtps() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}")
  def reqPostOtp(userId:UUID,secret:String,name:String,account:String,issuer:Option[String], period:Option[Int]) = 
      HttpRequest(method = HttpMethods.POST, uri = s"${uri}",
        entity = HttpEntity(ContentTypes.`application/json`, 
          OtpCreateReq(userId,secret,name,account,issuer,period).toJson.toString)
      )
  def reqDeleteOtp(id:UUID) = HttpRequest(method = HttpMethods.DELETE, uri = s"${uri}/${id}")

  def delete(id:UUID):Future[OtpActionRes] = {
    log.info(s"${id} -> ${reqDeleteOtp(id)}")
    for {
      rsp <- Http().singleRequest(reqDeleteOtp(id))
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[OtpActionRes] else Future(OtpActionRes(status=s"${rsp.status}: ${rsp.entity}",None))
    } yield r 
  }

  def create(userId:UUID,secret:String,name:String,account:String,issuer:Option[String],period:Option[Int]):Future[OtpCreateRes] = {
    log.info(s"${userId} -> ${reqPostOtp(userId,secret,name,account,issuer,period)}")
    for {
      rsp <- Http().singleRequest(reqPostOtp(userId,secret,name,account,issuer,period))
      r <- Unmarshal(rsp).to[OtpCreateRes]
    } yield r
  }

  def get(id:UUID):Future[Option[Otp]] = {
    log.info(s"${id} -> ${reqGetOtp(id)}")
    for {
      rsp <- Http().singleRequest(reqGetOtp(id))
      otp <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Otp]] else Future(None)
    } yield otp 
  }

  def getForUser(userId:UUID):Future[Otps] = {
    log.info(s"${userId} -> ${reqGetOtpForUser(userId)}")
    for {
      rsp <- Http().singleRequest(reqGetOtpForUser(userId))
      otps <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Otps] else Future(Otps(Seq()))
    } yield otps 
  }

  def all():Future[Otps] = {
    log.info(s" -> ${reqGetOtps()}")
    for {
      rsp <- Http().singleRequest(reqGetOtps())
      body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield body.utf8String.parseJson.convertTo[Otps]
  }
}

object OtpClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "OtpClientHttp")
  implicit val ec = system.executionContext

  def apply(uri:String):OtpClientHttp = {
    new OtpClientHttp(uri)
  }
}
