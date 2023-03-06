package io.syspulse.skel.auth.store

import scala.util.{Try,Success,Failure}
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
  
  def all:Seq[Auth] = store.all
  def size:Long = store.size
  override def +(a:Auth):Try[AuthStoreDir] = super.+(a).flatMap(_ => store.+(a)).map(_ => this)
  override def !(aid:String,accessToken:String,rereshToken:String,uid:Option[UUID]):Try[Auth] = {
    for {
      a <- store.!(aid,accessToken,rereshToken,uid)
      _ <- writeFile(a)
    } yield(a) 
  }
    
  override def del(aid:String):Try[AuthStoreDir] = super.del(aid).flatMap(_ => store.del(aid)).map(_ => this)
  override def ?(aid:String):Try[Auth] = store.?(aid)
  override def getForUser(uid:UUID):Seq[Auth] = store.getForUser(uid)

  // preload
  load(dir)

}