package io.syspulse.skel.cli.http.ipconfig

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

import akka.actor.ActorSystem
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
import java.math.BigInteger

final case class UserAgent(
  product: String,
  version: String,
  raw_value: String
)

final case class IpConfig(
  ip: String,
  ip_decimal: BigInt,
  country: String,
  country_iso: String,
  country_eu: Boolean,
  region_name: String,
  region_code: String,
  zip_code: String,
  city: String,
  latitude: Double,
  longitude: Double,
  time_zone: String,
  asn: String,
  asn_org: String,
  hostname: String,
  user_agent: UserAgent
)

object IpConfigJson extends JsonCommon {  
  import DefaultJsonProtocol._

  implicit val js2 = jsonFormat3(UserAgent)
  implicit val js1 = jsonFormat16(IpConfig.apply _) // this is needed to ignore companion object
}

class HttpClient(uri:String = HttpClient.serviceUri)(implicit as:ActorSystem, ec:ExecutionContext) {
  val log = Logger(s"${this}")

  import IpConfigJson._
  import spray.json._

  def reqIp(data:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/")
  def reqIpJson(data:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/json")
  
  def getIp(data:String = ""):Future[IpConfig] = {
    for {
      rsp <- Http().singleRequest(reqIpJson(data))
      ipconfig <- Unmarshal(rsp).to[IpConfig]
    } yield ipconfig
  }

  def getIp2(data:String = ""):Future[IpConfig] = {
    for {
      rsp <- Http().singleRequest(reqIpJson(data))
      body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield {
      val rsp = body.utf8String
      log.info(s"rsp='${rsp}'")
      rsp.parseJson.convertTo[IpConfig]
    }
  }
}

object HttpClient {
  val serviceUri = "http://ipconfig.io"
  def apply(uri:String = serviceUri,data:String = "",timeout:Duration = Duration("5 seconds"))(implicit system:ActorSystem, ec:ExecutionContext) = {
    val f = new HttpClient(uri).getIp2(data)
    Await.result(f, timeout)
  }
}
