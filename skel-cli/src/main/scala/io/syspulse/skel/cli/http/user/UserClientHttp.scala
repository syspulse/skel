package io.syspulse.skel.cli.http.user

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


class UserClientHttp(uri:String)(implicit system:ActorSystem[_], ec:ExecutionContext) {
  val log = Logger(s"${this}")

  import UserJson._
  import spray.json._

  def reqHealth() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/health")
  def req1(userId:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/${userId}")
  
  def discard() = {
    Http().singleRequest(reqHealth()).onComplete {
      case Success(res) => { 
        log.info(s"${res}: ${res.status}: ${res.entity} ")
        res.discardEntityBytes()
      }
      case Failure(e)   => log.error(s"${e}")
    }
  }

  def getHealthUser(userId:String):Try[Users] = {
    val f = Http().singleRequest(req1(userId))
    
    val r:Future[Try[Users]] = f.map( res => {
      res match {
        case HttpResponse(StatusCodes.OK, headers, entity, _) => { 
          log.info(s"${res}: ${res.status}: ${res.entity}")
        
          val userFuture2 = for{
            body <- res.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
          } yield body.utf8String.parseJson.convertTo[Users]
          
          val userFuture: Future[Users] = Unmarshal(res).to[Users]

          val users1 = Await.result(userFuture, Duration("5 seconds"))
          val users2 = Await.result(userFuture2, Duration("5 seconds"))
    
          Success(users1)
        }
        case rsp @ HttpResponse(status, _, entity, _) => {
          entity.dataBytes.runFold(ByteString(""))(_ ++ _).foreach { body =>
            log.error(s"${res}: ${status}: ${entity}: ${body.utf8String}")
          }
          //rsp.discardEntityBytes()
          Failure(new Exception(s"failed: status=${status}"))
        }
        case _  => {
          log.error(s"Unknown error: ")
          Failure(new Exception(s"failed: "))
        }
      }
    })
    val users = Await.result(r, Duration("5 seconds"))
    users
  }

  def getHealthUserFuture(userId:String) = {
    for {
      rsp <- Http().singleRequest(req1(userId))
      users <- Unmarshal(rsp).to[Users]
    } yield users 
  }

  def getHealthUserFuture2(userId:String) = {
    for {
      rsp <- Http().singleRequest(req1(userId))
      users <- for{
                  body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
                } yield body.utf8String.parseJson.convertTo[Users]
    } yield users 
  }
}

object UserClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "Service-Shell")
  implicit val ec = system.executionContext

  def apply(uri:String,userId:String) = {
    val f = new UserClientHttp(uri).getHealthUserFuture2(userId)
    val users = Await.result(f, Duration("5 seconds"))
  }
}
