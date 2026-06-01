package io.syspulse.skel.auth.store

import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.store.Store

trait AuthStore extends Store[Auth,String] {
  def getKey(auth: Auth): String = auth.accessToken

  def +(auth:Auth):Future[Auth]
  // def -(auth:Auth):Future[AuthStore]
  def del(aid:String):Future[String]
  def ?(aid:String):Future[Auth]
  def all:Future[Seq[Auth]]

  def findUser(uid:UUID):Future[Seq[Auth]]
  def size:Future[Long]

  def !(aid:String,accessToken:String,refreshToken:String,uid:Option[UUID]):Future[Auth]
}
