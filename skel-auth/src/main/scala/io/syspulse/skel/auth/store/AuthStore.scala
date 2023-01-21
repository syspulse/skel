package io.syspulse.skel.auth.store

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.store.Store

trait AuthStore extends Store[Auth,String] {
  
  def +(auth:Auth):Try[AuthStore]
  def -(auth:Auth):Try[AuthStore]
  def del(auid:String):Try[AuthStore]
  def ?(auid:String):Option[Auth]
  def all:Seq[Auth]
  def getForUser(userId:UUID):Seq[Auth]
  def size:Long
}

