package io.syspulse.skel.user.client

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.util.ByteString
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.settings.ClientConnectionSettings

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
import io.syspulse.skel.user.server.UserJson
import io.syspulse.skel.user.server.{Users, UserCreateReq, UserActionRes}

class UserClientHttp(uri:String)(implicit as:ActorSystem[_], ec:ExecutionContext) extends ClientHttp(uri)(as,ec) with UserService {
  
  import UserJson._
  import spray.json._
  
  def reqGetUser(id:UUID) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/${id}",headers=authHeaders())
  def reqGetUserByEid(xid:String) = HttpRequest(method = HttpMethods.GET, uri = s"${uri}/xid/${xid}",headers=authHeaders())
  def reqGetUsers() = HttpRequest(method = HttpMethods.GET, uri = s"${uri}",headers=authHeaders())
  def reqPostUser(email:String,name:String,xid:String,avatar:String) =  HttpRequest(method = HttpMethods.POST, uri = s"${uri}", headers=authHeaders(),
        entity = HttpEntity(ContentTypes.`application/json`, 
          UserCreateReq(email,name,xid,avatar).toJson.toString)
      )
  def reqDeleteUser(id:UUID) = HttpRequest(method = HttpMethods.DELETE, uri = s"${uri}/${id}",headers=authHeaders())

  def delete(id:UUID):Future[UserActionRes] = {
    log.info(s"${id} -> ${reqDeleteUser(id)}")
    for {
      rsp <- Http().singleRequest(reqDeleteUser(id))
      r <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[UserActionRes] else Future(UserActionRes(status=s"${rsp.status}: ${rsp.entity}",None))
    } yield r 
  }

  def create(email:String,name:String,xid:String,avatar:String):Future[Try[User]] = {
    log.info(s"-> ${reqPostUser(email,name,xid,avatar)}")
    for {
      rsp <- Http().singleRequest(reqPostUser(email,name,xid,avatar))      
      user <- if(rsp.status == StatusCodes.OK || rsp.status == StatusCodes.Created) Unmarshal(rsp).to[Option[User]] else Future(None)
      rsp <- user match {
        case Some(user) => Future(Success(user))
        case None => Future(Failure(new Exception(s"${Unmarshal(rsp).to[String]}")))
      }
    } yield rsp

    // for {
    //   rsp <- Http().singleRequest(reqPostUser(email,name,xid))
    //   body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
    // } yield {
    //   val data = body.utf8String
    //   log.info(s"data=${data}")
    //   data.parseJson.convertTo[Option[User]]
    // }
  }

  def get(id:UUID):Future[Try[User]] = {
    log.info(s"${id} -> ${reqGetUser(id)}")
    for {
      rsp <- Http().singleRequest(reqGetUser(id))
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
      rsp <- user match {
        case Some(user) => Future(Success(user))
        case None => Future(Failure(new Exception(s"${Unmarshal(rsp).to[String]}")))
      }
    } yield rsp
  }

  def findByXid(xid:String):Future[Option[User]] = {
    log.info(s"${xid} -> ${reqGetUserByEid(xid)}")    
    for {
      rsp <- Http().singleRequest(reqGetUserByEid(xid))
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
    } yield user
  }

  def findByXidAlways(xid:String):Future[Option[User]] = {
    log.info(s"${xid} -> ${reqGetUserByEid(xid)}")    
    
    for {
      rsp <- Http().singleRequest(reqGetUserByEid(xid),
                                  settings = ConnectionPoolSettings(UserClientHttp.system)
                                    .withConnectionSettings(ClientConnectionSettings(UserClientHttp.system)
                                    .withConnectingTimeout(FiniteDuration(3,"seconds"))))
      .transform {
        case Failure(e) => {
          log.error(s"Failed to call -> ${reqGetUserByEid(xid)}: ${e.getMessage()}")
          Try(HttpResponse(StatusCodes.InternalServerError))
        }
        case Success(r) => {
          Try(r)
        }
      }
      user <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[User]] else Future(None)
    } yield user
  }

  def findByEmail(email:String):Future[Option[User]] = {
    Future.failed(new NotImplementedError(s""))
  }

  // def all():Future[Users] = {
  //   log.info(s" -> ${reqGetUsers()}")
  //   for {
  //     rsp <- Http().singleRequest(reqGetUsers())
  //     body <- rsp.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
  //   } yield body.utf8String.parseJson.convertTo[Users]
  // }
  def all():Future[Try[Users]] = {
    log.info(s" -> ${reqGetUsers()}")
    for {
      rsp <- Http().singleRequest(reqGetUsers())
      users <- if(rsp.status == StatusCodes.OK) Unmarshal(rsp).to[Option[Users]] else Future(None)
      rsp <- users match {
        case Some(users) => Future(Success(users))
        case None => Future(Failure(new Exception(s"${Unmarshal(rsp).to[String]}")))
      }
    } yield rsp
  }

}

object UserClientHttp {
  implicit val system = ActorSystem(Behaviors.empty, "UserClientHttp")
  implicit val ec = system.executionContext

  def apply(uri:String):UserClientHttp = {
    new UserClientHttp(uri)
  }
}
