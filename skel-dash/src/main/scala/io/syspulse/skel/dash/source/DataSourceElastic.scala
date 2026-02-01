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
import java.util.Base64
import akka.http.scaladsl.model.headers.BasicHttpCredentials

import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.uri.ElasticURI
import io.syspulse.skel.dash.server.DashDataReq

import io.syspulse.skel.db.guard.QueryGuard
import io.syspulse.skel.db.guard.QueryGuardAllow
import io.syspulse.skel.dash.source.DataSource

class DataSourceElastic(uri0:String,fw:QueryGuard = QueryGuardAllow) extends DataSource {
  private val log = Logger(this.getClass)  

  val elasticUri = ElasticURI(uri0)
  
  val (uri,user,pass,limit,timeout,threads,compress) = (
    elasticUri.url,
    elasticUri.user,
    elasticUri.pass,
    elasticUri.ops.get("limit").map(_.toInt).getOrElse(DEF_LIMIT),
    elasticUri.ops.get("timeout").map(_.toInt).getOrElse(10000),
    elasticUri.ops.get("threads").map(_.toInt).getOrElse(8),
    elasticUri.ops.get("compress").map(_.toBoolean).getOrElse(false)
  )
    
  implicit val sys: ActorSystem = ActorSystem("DataSourceElastic")
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))
  
  // Configure longer timeouts
  private val poolSettings = ConnectionPoolSettings(sys)
    .withMaxConnections(32)
    .withMaxOpenRequests(64)
    .withPipeliningLimit(1)
    .withIdleTimeout(FiniteDuration(timeout, TimeUnit.MILLISECONDS))

  def src: String = "elastic"  

  def ask(req:DashDataReq, tid:Option[String] = None): Future[DashData] = {
    if(req.src != this.src) {
      return Future.failed(new Exception(s"unsupported datasource: '${req.src}'"))
    } 

    val ts0 = System.currentTimeMillis()
        
    val queryStr = req.typ match {
      case Some("sql") => 
        req.query.getOrElse(JsString("")).toString()        
      case _ =>
        req.query.getOrElse(JsObject()).toString()        
    } 

    // Validate query 
    val opts0: Map[String, Any] =
      if(tid.isDefined) Map("tenantId" -> tid.get) else Map[String,Any]()

    // Tell QueryGuard which language to parse (SQL vs Elastic DSL)
    val opts = opts0 ++ (req.typ match {
      case Some("sql") => Map("lang" -> "sql")
      case _ => Map("lang" -> "elastic")
    })
    fw.isAllowed(queryStr, opts) match {
      case Success(true) => // Continue processing
      case Success(false) => 
        return Future.failed(new Exception("Query Rejected"))
      case Failure(e) => 
        return Future.failed(new Exception(s"Query validation failed: ${e.getMessage}"))
    }

    val (h,u,body) = req.typ match {
      case Some("sql") => 
        val u = "/_plugins/_sql"
        val q = queryStr
        val body = s"""{"query": ${q}}"""
        (HttpMethods.POST,u,body)
      case _ =>
        val u = s"/${req.id}/_search"
        val body = queryStr
        (HttpMethods.GET,u,body)
    } 

    val auth = pass.map(_ => {
      val auth = BasicHttpCredentials(user.get, pass.get)
      RawHeader("Authorization", s"${auth}")
    })

    val request = HttpRequest(
      method = h,
      uri = s"${uri}${u}",
      entity = HttpEntity(ContentTypes.`application/json`, body),
      headers = List(

      ) 
      ++ (if(auth.isDefined) List(auth.get) else List())
      ++ (if(compress) List(RawHeader("Accept-Encoding", "gzip, deflate")) else List())
    )
    
    log.debug(s"id(${req.id}): body=${body}")
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
              fmt = "elastic",
              data = json,
              ts0 = ts0,
              ts = System.currentTimeMillis()
            )
          }
        case _ =>
          Unmarshal(entity).to[String].flatMap { err =>
            log.warn(s"id(${req.id}): ${r.status}: ${entity}: ${err}")
            Future.failed(new Exception(s"failed to call Elastic: ${r.status}: ${err}"))
          }
      }
    }
  }
}
