package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.auth.cred.CredStoreMem
import io.syspulse.skel.store.StoreDir
import CredJson._

// Preload from file during start
class CredStoreDir(dir:String = "store/cred/") extends StoreDir[Cred,String](dir) with CredStore {
  val store = new CredStoreMem
  
  def all:Seq[Cred] = store.all
  def size:Long = store.size
  override def +(c:Cred):Try[CredStoreDir] = super.+(c).flatMap(_ => store.+(c)).map(_ => this)

  override def del(cid:String):Try[CredStoreDir] = super.del(cid).flatMap(_ => store.del(cid)).map(_ => this)
  override def ?(cid:String):Try[Cred] = store.?(cid)

  // preload
  load(dir)

}