package io.syspulse.skel.auth.cid

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait ClientStore extends Store[Client,String] {
  
  def +(client:Client):Try[ClientStore]
  // def !(client:Client):Try[ClientStore]
  def -(client:Client):Try[ClientStore]
  def del(cid:String):Try[ClientStore]
  def ?(cid:String):Try[Client]
  def all:Seq[Client]
  def size:Long
}

