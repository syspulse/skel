package io.syspulse.skel.notify.http

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
import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.NotifySeverity
import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.Config

object NotifyHttp {
  implicit val as:actor.ActorSystem = actor.ActorSystem("NotifyHttp-System")
  implicit val ec = as.getDispatcher
}

case class NotifyHttp(uri:String,timeout:Long = 3000L)(implicit config: Config) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  override def toString = s"${this.getClass.getSimpleName}(${request})"

  case class Request(uri:String,verb:HttpMethod,headers:Map[String,String]=Map(),body:Option[String] = None,async:Boolean=false) {
    def getHeaders:Seq[HttpHeader] = headers.map{ case(k,v) => RawHeader(k,v) }.toSeq 

    def withUri(subj:String,msg:String) = {
      verb.value match {
        case "GET" | "AGET" | "GET:ASYNC" =>
          this.copy(uri = uri.replaceAll("\\{subj\\}",subj).replaceAll("\\{msg\\}",msg))
        case "POST" | "PUT" | "APOST" | "APUT" | "POST:ASYNC" | "PUT:ASYNC" =>
          this.copy(uri = uri.replaceAll("\\{subj\\}",subj), body = Some(msg) )
      }
      
    }
  }

  // http://host:port/url{subj}/{msg}
  // http://GET@host:port/url{subj}/{msg}
  // http://POST@host:port/url{subj}
  // http://POST@123456789@host:port/url{subj}
  // http://POST@{VAR}@host:port/url{subj}
  //
  // http://GET:ASYNC@host:port/url{subj}/{msg}
  // http://POST:ASYNC@host:port/url{subj}/{msg}
  // replace {} with real data
  def parseUri(uri:String) = {

    def buildRequest(proto:String,verb:HttpMethod,rest:String,headers:Map[String,String]=Map(),body:Option[String]=None,async:Boolean=false) = {
      Request(s"${proto}://${rest}",verb, headers, body, async)
    }

    def getAuth(auth:String):Map[String,String] = {
      val authToken = 
        if(auth.startsWith("{"))
          Some(Util.replaceEnvVar(auth))
        else
        if(!auth.isEmpty())
          Some(auth)
        else
        if(!config.httpAuth.isEmpty())
          Some(config.httpAuth)
        else
          None

      if(authToken.isDefined)
        Map("Authorization" -> s"Bearer ${authToken.get}")
      else
        Map()
    }

    uri.split("(://|@)").toList match {
      case proto :: "GET:ASYNC" :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest,async=true)
      case proto :: "POST:ASYNC" :: rest :: Nil => buildRequest(proto,HttpMethods.POST,rest,async=true)
      case proto :: "PUT:ASYNC" :: rest :: Nil => buildRequest(proto,HttpMethods.PUT,rest,async=true)
      case proto :: "DELETE:ASYNC" :: rest :: Nil => buildRequest(proto,HttpMethods.DELETE,rest,async=true)

      case proto :: "GET:ASYNC" :: auth :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest,getAuth(auth),async=true)
      case proto :: "POST:ASYNC" :: auth  :: rest :: Nil  => buildRequest(proto,HttpMethods.POST,rest,getAuth(auth),async=true)
      case proto :: "PUT:ASYNC" :: auth  :: rest :: Nil => buildRequest(proto,HttpMethods.PUT,rest,getAuth(auth),async=true)
      case proto :: "DELETE:ASYNC" :: auth  :: rest :: Nil => buildRequest(proto,HttpMethods.DELETE,rest,getAuth(auth),async=true)
      
      case proto :: "GET" :: auth :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest,getAuth(auth))
      case proto :: "POST" :: auth  :: rest :: Nil  => buildRequest(proto,HttpMethods.POST,rest,getAuth(auth))
      case proto :: "PUT" :: auth  :: rest :: Nil => buildRequest(proto,HttpMethods.PUT,rest,getAuth(auth))
      case proto :: "DELETE" :: auth  :: rest :: Nil => buildRequest(proto,HttpMethods.DELETE,rest,getAuth(auth))

      case proto :: "GET" :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest)
      case proto :: "POST" :: rest :: Nil => buildRequest(proto,HttpMethods.POST,rest)
      case proto :: "PUT" :: rest :: Nil => buildRequest(proto,HttpMethods.PUT,rest)
      case proto :: "DELETE" :: rest :: Nil => buildRequest(proto,HttpMethods.DELETE,rest)
      
      case proto :: "AGET" :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest,async=true)
      case proto :: "APOST" :: rest :: Nil => buildRequest(proto,HttpMethods.POST,rest,async=true)
      case proto :: "APUT" :: rest :: Nil => buildRequest(proto,HttpMethods.PUT,rest,async=true)
      case proto :: "ADELETE" :: rest :: Nil => buildRequest(proto,HttpMethods.DELETE,rest,async=true)

      case proto :: rest :: Nil => buildRequest(proto,HttpMethods.GET,rest)

      case u => buildRequest("http",HttpMethods.GET,"localhost:8080")
    }
  }

  val request = parseUri(uri)

  def ->(r:Request) =  
    if(r.body.isDefined)
      HttpRequest(method = r.verb, uri = r.uri, headers = r.getHeaders,
                  entity = HttpEntity(ContentTypes.`application/json`, r.body.get))
    else
      HttpRequest(method = r.verb, uri = r.uri, headers = r.getHeaders)
  
  
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
      Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS)).map(_.utf8String)
    else 
      Success(f.toString)    
  }

  def send(no:Notify):Try[String] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}

