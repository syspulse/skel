package io.syspulse.skel.wf.exec

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

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util
import io.syspulse.skel.notify.client._

import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

object HttpClientExec {
  implicit val as:actor.ActorSystem = actor.ActorSystem("HttpClient-System")
  implicit val ec = as.getDispatcher
}

class HttpClientExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val uri = dataExec.get("http.uri").getOrElse("http://localhost:8300").asInstanceOf[String]
  val auth = dataExec.get("http.auth").getOrElse("").asInstanceOf[String]

  import HttpClientExec._
  val timeout = FiniteDuration(10,TimeUnit.SECONDS)
  
  case class Request(uri:String,verb:HttpMethod,headers:Map[String,String]=Map(),body:Option[String] = None,async:Boolean=false,json:Boolean=false) {
    def getHeaders:Seq[HttpHeader] = headers.map{ case(k,v) => RawHeader(k,v) }.toSeq 

    def withJson(body:Option[String]) = {
      this.copy(
        body = body,
        json = true
      )
    }

    def getEntity = {
      if(json)
        HttpEntity(ContentTypes.`application/json`,body.getOrElse(""))
      else
        HttpEntity.Empty
    }

    def withAuth(auth:String) = {
      if(auth.isEmpty())
        this
      else
        this.copy(headers = headers + ("Authorization" -> auth))
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
  
  def request(uri:String,body:Option[String]) = {
    val r = parseUri(uri).withAuth(auth).withJson(body)
    HttpRequest(
      method = r.verb, 
      uri = r.uri, 
      headers = r.getHeaders,
      entity = r.getEntity)    
  }
  
  def --->(uri:String,body:Option[String]):Try[String] = {
    
    val req = request(uri,body)
    log.info(s"${req}")

    val f:Future[Try[ByteString]] = for {
      r0 <- try {
          Http().singleRequest(req).map(Success(_))
        } catch {
          case e:Exception => Future(Failure(e))
        }
      r1 <- { r0 match {
        case Success(rsp) => 
          val f:Future[Try[ByteString]] = if(rsp.status == StatusCodes.OK) {
            val f = rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
            f.map(Success(_))            
          } else 
            Future(Failure(new Exception(s"${rsp.status}")))
          f
        case Failure(e) => Future(Failure(e))
      }}
      
    } yield r1

    try {
      Await.result(f,FiniteDuration(3000L,TimeUnit.MILLISECONDS)).map(_.utf8String)
    } catch {
      case e:Exception => 
        log.warn(s"send failed:",e)
        Failure(e)
    }
    
    // val f = for {
    //   rsp <- Http().singleRequest(req)
    //   r <- if(rsp.status == StatusCodes.OK) {
    //       val f = rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    //       f.map(Success(_))
    //     } else 
    //       Future(Failure(new Exception(s"${rsp.status}")))        
    // } yield r

    // Await.result(f,FiniteDuration(3000L,TimeUnit.MILLISECONDS)).map(_.utf8String)
    // // if(!req.async)
    // //   Await.result(f,FiniteDuration(3000L,TimeUnit.MILLISECONDS)).map(_.utf8String)
    // // else 
    // //   Success(f.toString)    
  }

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    var body = getAttr("http.body",data).getOrElse("").asInstanceOf[String]
                                
    val data1 = --->(uri,Option.when(!body.isEmpty)(body)) match {
      case Success(res) => 
        data.copy(attr = data.attr + ("http.res" -> res) )
      case f => 
        data
    }
    
    broadcast(data1)
    Success(ExecDataEvent(data1))
  }
}

