package io.syspulse.dash.source

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

import io.syspulse.dash.server.DashData
import spray.json.JsObject
import spray.json.JsString
import io.syspulse.dash.source.DuneURI
import io.syspulse.dash.server.DashDataReq
import io.syspulse.dash.source.DataSource

class DataSourceDune(uri:String) extends DataSource {
  private val log = Logger(this.getClass)
  private val baseUrl = "https://api.dune.com/api/v1"

  val duneUri = DuneURI(uri)
  val (apiKey,limit,timeout,threads,compress) = (
    duneUri.apiKey,
    duneUri.limit,
    duneUri.timeout,
    duneUri.ops.get("threads").map(_.toInt).getOrElse(8),
    duneUri.ops.get("compress").map(_.toBoolean).getOrElse(true)
  )
    
  implicit val sys: ActorSystem = ActorSystem("DataSourceDune")
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))
  
  // Configure longer timeouts
  private val poolSettings = ConnectionPoolSettings(sys)
    .withMaxConnections(32)
    .withMaxOpenRequests(64)
    .withPipeliningLimit(1)
    .withIdleTimeout(FiniteDuration(timeout, TimeUnit.MILLISECONDS))
  
  def src: String = "dune"  
  
  def ask(req:DashDataReq, tid:Option[String] = None): Future[DashData] = {
    if(req.src != this.src) {
      return Future.failed(new Exception(s"unsupported datasource: '${req.src}'"))
    } 

    val ts0 = System.currentTimeMillis()

    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$baseUrl/query/${req.id}/results?limit=${req.limit.getOrElse(DEF_LIMIT)}",
      headers = List(
        RawHeader("x-dune-api-key", apiKey),
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
              fmt = "dune",
              data = json,
              ts0 = ts0,
              ts = System.currentTimeMillis()
            )
          }
        case _ =>          
          Unmarshal(entity).to[String].flatMap { err =>
            log.warn(s"id(${req.id}): request failed: ${r.status}: ${err}")
            Future.failed(new Exception(s"failed to call Dune: ${r.status}: ${err}"))
          }
      }
    }
  }
}
