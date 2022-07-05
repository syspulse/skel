package io.syspulse.skel.user.client

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

import io.syspulse.skel.ClientHttp
import io.syspulse.skel.util.Util
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.user._
import io.syspulse.skel.user.UserJson
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.settings.ClientConnectionSettings

class UserClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends ClientHttp[UserClientHttp](uri)(as,ec) {
  
  import UserJson._
  import spray.json._
  
  def reqGetUser(id:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/${id}")
  def reqGetUserByEid(eid:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/eid/${eid}")
  def reqGetUsers() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}")
  def reqPostUser(email:String,name:String,eid:String) =  HttpRequest(method = HttpMethods.POST, uri = s"${uri}",
        entity = HttpEntity(ContentTypes.`application/json`, 
          UserCreateReq(email,name,eid).toJson.toString)
      )
  def reqDeleteUser(id:UUID) = HttpRequest(method = HttpMethods.DELETE, uri = s"${uri}/${id}")

  def delete(id:UUID):Future[UserActionRes] = {
    log.info(s"${id} -> ${reqDeleteUser(id)}")
    for {
      rsp <- Http().singleRequest(reqDeleteUser(id))
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[UserActionRes] else Future(UserActionRes(status=s"${rsp.status}: ${rsp.entity}",None))
    } yield r 
  }

  def create(email:String,name:String,eid:String):Future[User] = {
    log.info(s"-> ${reqPostUser(email,name,eid)}")
    for {
      rsp <- Http().singleRequest(reqPostUser(email,name,eid))
      r <- Unmarshal(rsp).to[User]
    } yield r
  }

  def get(id:UUID):Future[Option[User]] = {
    log.info(s"${id} -> ${reqGetUser(id)}")
    for {
      rsp <- Http().singleRequest(reqGetUser(id))
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
    } yield user 
  }

  def getByEid(eid:String):Future[Option[User]] = {
    log.info(s"${eid} -> ${reqGetUserByEid(eid)}")    
    for {
      rsp <- Http().singleRequest(reqGetUserByEid(eid))        
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
    } yield user
  }

  def getByEidAlways(eid:String):Future[Option[User]] = {
    log.info(s"${eid} -> ${reqGetUserByEid(eid)}")    
    
    for {
      rsp <- Http().singleRequest(reqGetUserByEid(eid),
                                  settings = ConnectionPoolSettings(UserClientHttp.system)
                                    .withConnectionSettings(ClientConnectionSettings(UserClientHttp.system)
                                    .withConnectingTimeout(FiniteDuration(3,"seconds"))))
      .transform {
        case Failure(e) => {
          log.error(s"Failed to call -> ${reqGetUserByEid(eid)}: ${e.getMessage()}")
          Try(HttpResponse(StatusCodes.InternalServerError))
        }
        case Success(r) => {
          Try(r)
        }
      }
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
    } yield user
  }

  def all():Future[Users] = {
    log.info(s" -> ${reqGetUsers()}")
    for {
      rsp <- Http().singleRequest(reqGetUsers())
      body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    } yield body.utf8String.parseJson.convertTo[Users]
  }
}

object UserClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "UserClientHttp")
  implicit val ec = system.executionContext

  def apply(uri:String):UserClientHttp = {
    new UserClientHttp(uri)
  }
}
