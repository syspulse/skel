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

import io.syspulse.skel.util.Util
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.otp._
import io.syspulse.skel.otp.OtpJson
import io.syspulse.skel.otp.OtpCreateResult


class FutureAwaitable[T](f:Future[T],timeout:Duration = FutureAwaitable.timeout)  {
  def await[R]() = Await.result(f,timeout)
}

object FutureAwaitable {
  val timeout = Duration("5 seconds")
  implicit def ftor[R](f: Future[R]) = new FutureAwaitable[R](f)
}


abstract class ClientHttp[T <: ClientHttp[T]](uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) {
  val log = Logger(s"${this}")
  var timeout:Duration = FutureAwaitable.timeout

  def getUri() = uri
  
  def reqHealth() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/health")
  def reqInfo() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/info")
  
  def discard() = {
    Http().singleRequest(reqHealth()).onComplete {
      case Success(res) => { 
        log.info(s"${res}: ${res.status}: ${res.entity} ")
        res.discardEntityBytes()
      }
      case Failure(e)   => log.error(s"${e}")
    }
  }

  def await[R](rsp:Future[R]):R = {
    val r = Await.result(rsp,timeout)
    r
  }

  def withTimeout(timeout:Duration):T = {
    this.timeout = timeout
    // a bit dirty
    this.asInstanceOf[T]
  }
}

class OtpClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends ClientHttp[OtpClientHttp](uri)(as,ec) {
  
  import OtpJson._
  import spray.json._
  
  def reqGetOtpForUser(userId:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/user/${userId}")
  def reqGetOtps() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}")
  def reqPostOtp(userId:UUID,secret:String,name:String,account:String,issuer:Option[String], period:Option[Int]) = 
      HttpRequest(method = HttpMethods.POST, uri = s"${uri}",
        entity = HttpEntity(ContentTypes.`application/json`, 
          OtpCreate(userId,secret,name,account,issuer,period).toJson.toString)
      )

  def create(userId:UUID,secret:String,name:String,account:String,issuer:Option[String],period:Option[Int]):Future[OtpCreateResult] = {
    log.info(s"${userId} -> ${reqPostOtp(userId,secret,name,account,issuer,period)}")
    for {
      rsp <- Http().singleRequest(reqPostOtp(userId,secret,name,account,issuer,period))
      r <- Unmarshal(rsp).to[OtpCreateResult]
    } yield r
  }

  def getForUser(userId:UUID):Future[Otps] = {
    log.info(s"${userId} -> ${reqGetOtpForUser(userId)}")
    for {
      rsp <- Http().singleRequest(reqGetOtpForUser(userId))
      otp <- Unmarshal(rsp).to[Otps]
    } yield otp 
  }

  def getAll():Future[Otps] = {
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
