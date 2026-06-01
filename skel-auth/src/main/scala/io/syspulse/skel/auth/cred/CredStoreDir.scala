package io.syspulse.skel.auth.cred

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
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

  def toKey(id:String):String = id
  def all:Future[Seq[Cred]] = store.all
  def size:Future[Long] = store.size
  override def +(c:Cred):Future[Cred] = super.+(c).flatMap(_ => store.+(c))

  override def del(cid:String):Future[String] = super.del(cid).flatMap(_ => store.del(cid))
  override def ?(cid:String):Future[Cred] = store.?(cid)

  override def update(id:String,secret:Option[String]=None,name:Option[String]=None,expire:Option[Long] = None):Future[Cred] =
    store.update(id,secret,name,expire).flatMap(c => Future.fromTry(writeFile(c)))

  // preload
  load(dir)

}
