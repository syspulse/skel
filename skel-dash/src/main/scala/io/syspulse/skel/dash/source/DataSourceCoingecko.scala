package io.syspulse.skel.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import java.util.concurrent.Executors
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{RawHeader, `Content-Encoding`}
import akka.http.scaladsl.settings.{ConnectionPoolSettings, ClientConnectionSettings}
import akka.http.scaladsl.unmarshalling.Unmarshal
import spray.json._
import akka.stream.scaladsl.Compression

import io.syspulse.skel.dash.server.DashData
import spray.json.JsObject
import spray.json.JsString
import io.syspulse.skel.dash.server.DashDataReq

import io.syspulse.skel.uri.CoingeckoURI
import io.syspulse.skel.coingecko.Coingecko
import io.syspulse.skel.dash.source.DataSource

class DataSourceCoingecko(uri:String,threads:Int=16) extends DataSource {
  private val log = Logger(this.getClass)

  implicit val sys: ActorSystem = ActorSystem("DataSourceCoingecko")
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))

  val coingecko = Coingecko(uri)
  val compress = coingecko.get.getUri().ops.get("compress").map(_.toBoolean).getOrElse(true)
  val baseUrl = coingecko.get.getUri().getBaseUrl()
  val timeout = coingecko.get.getUri().timeout
  val apiKey = coingecko.get.getUri().apiKey
    
  // Configure longer timeouts
  private val poolSettings = ConnectionPoolSettings(sys)
    .withMaxConnections(32)
    .withMaxOpenRequests(64)
    .withPipeliningLimit(1)
    .withIdleTimeout(timeout)
  
  def src: String = "coingecko"
  
  def ask(req:DashDataReq, tid:Option[String] = None): Future[DashData] = {
    if(req.src != this.src) {
      return Future.failed(new Exception(s"unsupported datasource: '${req.src}'"))
    } 

    val ts0 = System.currentTimeMillis()

    val urlSuffix = req.query match {
      case Some(JsString(q)) => q
        
      case _ => 
        log.error(s"id(${req.id}): invalid params: query=${req.query},typ=${req.typ}")
        throw new Exception(s"invalid params")
    }

    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"${baseUrl}/${urlSuffix}",
      headers = List(
        RawHeader("x-cg-pro-api-key", apiKey),
        // RawHeader("Accept-Encoding", "gzip, deflate")
      ) ++ (if(compress) List(RawHeader("Accept-Encoding", "gzip, deflate")) else List())
    )
    
    log.info(s"id(${req.id}) -> ${request.uri}")

    Http().singleRequest(request, settings = poolSettings).flatMap { r =>

      val entity = r.header[`Content-Encoding`] match {
            case Some(enc) if enc.encodings.exists(_.value == "gzip") =>
              r.entity.transformDataBytes(Compression.gunzip())
            case _ => r.entity
          }
          
      r.status match {        
        case StatusCodes.OK =>
          Unmarshal(entity).to[String].map { j =>
            val json = JsonParser(j).asJsObject
            log.info(s"id(${req.id}): rsp=(${j.size} bytes)")
            DashData(
              id = req.id,
              src = src,
              fmt = "coingecko",
              data = json,
              ts0 = ts0,
              ts = System.currentTimeMillis()
            )
          }
        case StatusCodes.UnprocessableEntity =>
          // 422 with chunked response means we need to process the chunks
          Unmarshal(entity).to[String].flatMap { j =>
            val json = JsonParser(j).asJsObject
            log.warn(s"id(${req.id}): ${r.status}: ${json}")
            Future.failed(new Exception(json.toString()))
          }
        // case StatusCodes.TooManyRequests =>
        //   // Handle rate limiting
        //   log.warn(s"id(${req.id}): ${r.status}: ${r.entity}")
        //   Future.failed(new Exception(s"${r.entity}"))
        // case StatusCodes.BadRequest =>
        //   // Handle 400 Bad Request
        //   Unmarshal(entity).to[String].flatMap { err =>
        //     log.warn(s"id(${req.id}): ${r.status}: ${err}")
        //     Future.failed(new Exception(err))
        //   }
        case _ =>          
          Unmarshal(entity).to[String].flatMap { err =>
            log.warn(s"id(${req.id}): ${r.status}: ${err}")
            Future.failed(new Exception(s"Coingecko API failed: ${r.status}: ${err}"))
          }
      }
    }
  }
}
