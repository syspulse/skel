package io.syspulse.skel.coingecko

import com.typesafe.scalalogging.Logger
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpMethods
import akka.util.ByteString


trait CoingeckoClient {
  val log = Logger(s"${this}")

  implicit val as: akka.actor.ActorSystem = ActorSystem("ActorSystem-CoingeckoClient")
  
  val COINGECKO_API_URL = "https://pro-api.coingecko.com/api/v3"

  def getBaseUri() = COINGECKO_API_URL

  // Perform a GET request to CoinGecko with API key header and return body as UTF-8 string
  def request(uri: String, apiKey: String)(implicit ec: ExecutionContext): Future[ByteString] = {
    val req = HttpRequest(
      uri = s"${uri}",
      method = HttpMethods.GET,
      headers = Seq(RawHeader("x-cg-pro-api-key", s"${apiKey}"))
    )

    log.info(s"--> ${req.uri}")

    Http()
      .singleRequest(req)
      .flatMap(res => {
        res.status match {
          case StatusCodes.OK =>
            val body = res.entity.dataBytes.runReduce(_ ++ _)
            body
          case StatusCodes.TooManyRequests =>
            val body = Await.result(
              res.entity.dataBytes.runReduce(_ ++ _),
              FiniteDuration(5000L, TimeUnit.MILLISECONDS)
            ).utf8String
            log.warn(s"${res.status}: ${req}: ${res}")
            throw new Exception(s"${req.uri}: ${res.status}")
          case _ =>
            val body = Await.result(
              res.entity.dataBytes.runReduce(_ ++ _),
              FiniteDuration(5000L, TimeUnit.MILLISECONDS)
            ).utf8String
            log.warn(s"${res.status}: ${req}: ${res}")
            throw new Exception(s"${req.uri}: ${res.status}")
        }
      })      
  }

  def requestCoins(ids:Set[String],apiKey: String)(implicit ec: ExecutionContext): Future[ByteString] = {
    if(ids.size > 0) {
      val list = ids.mkString(",")
      request(s"${COINGECKO_API_URL}/coins/${list}",apiKey)
    } else {
      request(s"${COINGECKO_API_URL}/coins/list",apiKey)
    }
  }
  
}
