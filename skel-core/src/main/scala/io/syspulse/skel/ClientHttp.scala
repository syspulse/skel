package io.syspulse.skel

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.collection.immutable

import akka.actor.typed.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader

import com.typesafe.scalalogging.Logger

abstract class ClientHttp[T <: ClientHttp[T]](uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends AwaitableService[T] {
  val log = Logger(s"${this}")

  var accessToken:Option[String] = None

  def getUri() = uri

  def authHeaders(jwt:Option[String] = None):Seq[HttpHeader] = {
    val token = Seq(jwt,accessToken,sys.env.get("ACCESS_TOKEN"),sys.props.get("ACCESS_TOKEN"))
      .find(_.isDefined).flatten.getOrElse("")
    
    Seq(RawHeader("Authorization",s"Bearer ${jwt}"))
  }
    
  
  def reqHealth() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/health")
  def reqInfo() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/info")
  
  def withAccessToken(token:String):T = {
    this.accessToken = Some(token)
    this.asInstanceOf[T]
  }

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
