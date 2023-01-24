package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CredStore extends Store[Cred,String] {
  
  def getKey(c: Cred): String = c.cid
  
  def +(c:Cred):Try[CredStore]
  // def !(client:Cred):Try[CredStore]
  //def -(c:Cred):Try[CredStore]
  def del(cid:String):Try[CredStore]
  def ?(cid:String):Try[Cred]
  def all:Seq[Cred]
  def size:Long
}

