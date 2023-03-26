package io.syspulse.skel.lake.job.livy

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorSystem
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.Future
import scala.util.{ Try, Failure, Success }

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.util.Util

import io.jvm.uuid._
import akka.http.scaladsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString
import akka.actor
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.lake.job._
import io.syspulse.skel.lake.job.livy._

object LivyHttp {
  implicit val as:actor.ActorSystem = actor.ActorSystem("LivyHttp-System")
  implicit val ec = as.getDispatcher
}

class LivyHttp(uri:String) extends JobEngine {
  val log = Logger(s"${this}")
  
  import LivyHttp._
  import LivyJson._

  override def toString = s"${this.getClass.getSimpleName}(${request})"

  case class Request(uri:String,verb:HttpMethod,headers:Map[String,String]=Map(),body:Option[String] = None,async:Boolean=false) {
    def getHeaders:Seq[HttpHeader] = headers.map{ case(k,v) => RawHeader(k,v) }.toSeq 

    // def withId(subj:String,msg:String) = {
    //   this.copy(uri = uri
    //     .replaceAll("\\{subj\\}",subj)
    //     .replaceAll("\\{msg\\}",msg)
    //   )
    // }
  }

  // http://POST/host:port/url{subj}/{msg}
  // replace {} with real data
  def parseUri(uri:String) = {

    def buildRequest(proto:String,verb:HttpMethod,rest:List[String],headers:Map[String,String]=Map(),body:Option[String]=None,async:Boolean=false) = {
      Request(s"${proto}://${rest.mkString("/")}",verb, headers, body)
    }

    uri.split("(://|/)").toList match {
      case proto :: "GET" :: rest => buildRequest(proto,HttpMethods.GET,rest)
      case proto :: "POST" :: rest => buildRequest(proto,HttpMethods.POST,rest)
      case proto :: "PUT" :: rest => buildRequest(proto,HttpMethods.PUT,rest)
      case proto :: "DELETE" :: rest => buildRequest(proto,HttpMethods.DELETE,rest)

      case proto :: "AGET" :: rest => buildRequest(proto,HttpMethods.GET,rest,async=true)
      case proto :: "APOST" :: rest => buildRequest(proto,HttpMethods.POST,rest,async=true)
      case proto :: "APUT" :: rest => buildRequest(proto,HttpMethods.PUT,rest,async=true)
      case proto :: "ADELETE" :: rest => buildRequest(proto,HttpMethods.DELETE,rest,async=true)

      case proto :: rest => buildRequest(proto,HttpMethods.GET,rest)      
    }
  }

  val request = parseUri(uri)

  def ->(r:Request) = {
    val req = HttpRequest(method = r.verb, uri = r.uri, headers = r.getHeaders, 
      entity = if(r.body.isDefined) HttpEntity(ContentTypes.`application/json`,r.body.get) else HttpEntity.Empty
    )
    //entity = HttpEntity(ContentTypes.`application/json`)
    log.info(s"-> ${req}")

    val f = for {
      rsp <- Http().singleRequest(req)
      r <- if(rsp.status == StatusCodes.OK || rsp.status == StatusCodes.Created) {
          val f = rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
          f.map(Success(_))
        } else 
          Future(Failure(new Exception(s"${rsp.status}")))        
    } yield r

    if(!request.async)
      Await.result(f,FiniteDuration(3000L,TimeUnit.MILLISECONDS)).map(_.utf8String)
    else 
      Success(f.toString) 
  }
   
  def toJob(sess:LivySession) = Job(
    xid = sess.id.toString,
    status = sess.state,
    src = "",
    log = Some(sess.log)
  )

  def toJob(job:Job,st:LivyStatement) = Job(
    xid = job.xid,
    status = job.status,
    src = st.code,
    log = job.log,
    res = Some(st.output.data.values.mkString("\n"))
  )

  def all():Try[Seq[Job]] = {
    val res = ->(Request(uri + "/sessions", HttpMethods.GET))
    log.info(s"res = ${res}")
    res.map(_.parseJson.convertTo[LivySessions].sessions.map(r => toJob(r)))
  }

  def ask(xid:String):Try[Job] = {
    val res = ->(Request(uri + s"/sessions/${xid}", HttpMethods.GET))
    log.info(s"res = ${res}")
    res.map(r => toJob(r.parseJson.convertTo[LivySession]))
  }

  def create(name:String,conf:Map[String,String]=Map()):Try[Job] = {
    val res = ->(Request(uri + s"/sessions", HttpMethods.POST, 
      body = Some(LivySessionCreate(kind = "pyspark", name, conf).toJson.compactPrint)
    ))
    log.info(s"res = ${res}")
    res.map(r => toJob(r.parseJson.convertTo[LivySession]))
  }

  def del(xid:String):Try[String] = {
    val res = ->(Request(uri + s"/sessions/${xid}" , HttpMethods.DELETE))
    log.info(s"res = ${res}")
    res.map(r => r.parseJson.convertTo[LivySessionRes].msg)
  }

  def run(job:Job,script:String):Try[Job] = {
    val res = ->(Request(uri + s"/sessions/${job.xid}/statements", HttpMethods.POST, 
      body = Some(LivySessionRun(code = script).toJson.compactPrint)
    ))
    log.info(s"res = ${res}")
    res.map(r => toJob(job,r.parseJson.convertTo[LivyStatement]))
  }
}

