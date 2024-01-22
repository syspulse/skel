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

  def findUser(uid:UUID):Seq[Auth] = {
    auths.values.filter(_.uid == Some(uid)).toSeq
  }

  def size:Long = auths.size

  def +(auth:Auth):Try[Auth] = { 
    auths = auths + (auth.accessToken -> auth); 
    log.info(s"Auth: ${auth}")
    Success(auth)
  }
  
  def del(aid:String):Try[String] = { 
    val sz = auths.size
    auths = auths - aid
    if(sz == auths.size) Failure(new Exception(s"not found: ${aid}")) else Success(aid)
  }

  // def -(auth:Auth):Try[AuthStore] = { 
  //   del(auth.accessToken)
  // }

  def ?(aid:String):Try[Auth] = auths.get(aid) match {
    case Some(a) => Success(a)
    case None => Failure(new Exception(s"not found: ${aid}"))
  }

  def !(aid:String,accessToken:String,refreshToken:String,uid:Option[UUID] = None):Try[Auth] = ?(aid).map( auth => {
    // remove old one
    this.del(aid)

    // add updated
    val auth1 = auth.copy(
      accessToken = accessToken, 
      refreshToken = Some(refreshToken), 
      uid = if(uid.isDefined) uid else auth.uid)
            
    this.+(auth1)
    auth1
  })
}
