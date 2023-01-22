package io.syspulse.skel

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.collection.immutable

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader

import com.typesafe.scalalogging.Logger

abstract class ClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) {
  val log = Logger(s"${this}")

  def getUri() = uri

  def authHeaders(jwt:String = sys.env.getOrElse("ACCESS_TOKEN","")):Seq[HttpHeader] = 
    Seq(RawHeader("Authorization",s"Bearer ${jwt}"))
  
  def reqHealth() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/health")
  def reqInfo() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/info")
  
  def discard() = {
    Http().singleRequest(reqHealth()).onComplete {
      case Success(res) => { 
        log.info(s"${res}: ${res.status}: ${res.entity} ")
        res.discardEntityBytes()
      }
      case Failure(e)   => log.error(s"${e}")
    }
  }
}
