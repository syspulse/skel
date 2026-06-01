package io.syspulse.skel.auth.store

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable
import io.jvm.uuid._

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.auth.server.AuthJson._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.auth.store.AuthStoreMem

// Preload from file during start
class AuthStoreDir(dir:String = "store/auth/") extends StoreDir[Auth,String](dir) with AuthStore {
  val store = new AuthStoreMem

  def toKey(id:String):String = id
  def all:Future[Seq[Auth]] = store.all
  def size:Future[Long] = store.size
  override def +(a:Auth):Future[Auth] = super.+(a).flatMap(_ => store.+(a))
  override def !(aid:String,accessToken:String,refreshToken:String,uid:Option[UUID]):Future[Auth] =
    store.!(aid,accessToken,refreshToken,uid).flatMap(a => Future.fromTry(writeFile(a)).map(_ => a))

  override def del(aid:String):Future[String] = super.del(aid).flatMap(_ => store.del(aid))
  override def ?(aid:String):Future[Auth] = store.?(aid)
  override def findUser(uid:UUID):Future[Seq[Auth]] = store.findUser(uid)

  // preload
  load(dir)

}
