package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorSystem
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.Future
import scala.util.{ Try, Failure, Success }

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

object NotifyHttp {
  implicit val as:actor.ActorSystem = actor.ActorSystem("NotifyHttp-System")
  implicit val ec = as.getDispatcher
}

class NotifyHttp(uri:String) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  override def toString = s"${this.getClass.getSimpleName}(${request})"

  case class Request(uri:String,verb:HttpMethod,headers:Map[String,String]=Map(),body:Option[String] = None,async:Boolean=false) {
    def getHeaders:Seq[HttpHeader] = headers.map{ case(k,v) => RawHeader(k,v) }.toSeq 

    def withUri(subj:String,msg:String) = {
      this.copy(uri = uri
        .replaceAll("\\{subj\\}",subj)
        .replaceAll("\\{msg\\}",msg)
      )
    }
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

  def ->(r:Request) =  
    HttpRequest(method = r.verb, uri = r.uri, headers = r.getHeaders
    //entity = HttpEntity(ContentTypes.`application/json`)
  )
  
  def send(subj:String,msg:String,severity:Option[NotifySeverity.ID],scopeOver:Option[String]):Try[String] = {
    import NotifyHttp._

    val req = ->(request.withUri(subj,msg))
    log.info(s"-> ${req}")
    
    val f = for {
      rsp <- Http().singleRequest(req)
      r <- if(rsp.status == StatusCodes.OK) {
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

  def send(no:Notify):Try[String] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}

