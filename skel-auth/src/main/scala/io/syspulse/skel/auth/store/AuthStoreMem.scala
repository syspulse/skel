package io.syspulse.skel.auth.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.auth.store.AuthStore

class AuthStoreMem extends AuthStore {
  val log = Logger(s"${this}")
  
  var auths: Map[String,Auth] = Map()

  def all:Seq[Auth] = auths.values.toSeq

  def getForUser(userId:UUID):Seq[Auth] = {
    auths.values.filter(_.uid == Some(userId)).toSeq
  }

  def size:Long = auths.size

  def +(auth:Auth):Try[AuthStore] = { 
    auths = auths + (auth.accessToken -> auth); 
    log.info(s"Auth: ${auth}")
    Success(this)
  }
  
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
