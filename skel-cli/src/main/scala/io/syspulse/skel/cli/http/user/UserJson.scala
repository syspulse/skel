package io.syspulse.skel.cli.http.user

import scala.collection.immutable
import io.jvm.uuid._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.service.JsonCommon

final case class User(userId:UUID, createdTs:Long, name: String)
final case class Users(users: immutable.Seq[User])

object UserJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val userJsonFormat = jsonFormat3(User.apply _) // this is needed to ignore companion object
  implicit val usersJsonFormat = jsonFormat1(Users)
}
