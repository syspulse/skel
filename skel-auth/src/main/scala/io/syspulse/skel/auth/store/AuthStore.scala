package io.syspulse.skel.auth.store

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.store.Store

trait AuthStore extends Store[Auth,String] {
  def getKey(auth: Auth): String = auth.accessToken

  def +(auth:Auth):Try[AuthStore]
  // def -(auth:Auth):Try[AuthStore]
  def del(aid:String):Try[AuthStore]
  def ?(aid:String):Try[Auth]
  def all:Seq[Auth]
  
  def getForUser(uid:UUID):Seq[Auth]
  def size:Long

  def !(auid:String,accessToken:String,rereshToken:String,uid:Option[UUID] = None):Try[Auth]
}

