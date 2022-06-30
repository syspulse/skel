package io.syspulse.skel.auth

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class AuthStoreMem extends AuthStore {
  val log = Logger(s"${this}")
  
  var auths: Map[String,Auth] = Map()

  def all:Seq[Auth] = auths.values.toSeq

  def getForUser(userId:UUID):Seq[Auth] = {
    auths.values.filter(_.uid == userId).toSeq
  }

  def size:Long = auths.size

  def +(auth:Auth):Try[AuthStore] = { auths = auths + (auth.accessToken -> auth); Success(this)}
  
  def del(token:String):Try[AuthStore] = { 
    val sz = auths.size
    auths = auths - token
    if(sz == auths.size) Failure(new Exception(s"not found: ${token}")) else Success(this)
  }

  def -(auth:Auth):Try[AuthStore] = { 
    del(auth.accessToken)
  }

  def ?(token:String):Option[Auth] = auths.get(token)
}
