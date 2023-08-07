package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.auth.permit.PermitsStoreMem
import io.syspulse.skel.store.StoreDir
import PermitsJson._

// Preload from file during start
class PermitsStoreDir(dir:String = "store/permissions/") extends StoreDir[Permits,UUID](dir) with PermitsStore {
  val store = new PermitsStoreMem

  def toKey(id:String):UUID = UUID(id)
  def all:Seq[Permits] = store.all
  def size:Long = store.size
  override def +(c:Permits):Try[PermitsStoreDir] = super.+(c).flatMap(_ => store.+(c)).map(_ => this)

  override def del(uid:UUID):Try[PermitsStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:UUID):Try[Permits] = store.?(uid)

  override def update(uid:UUID,permissions:Option[Seq[Perm]]):Try[Permits] =
    store.update(uid,permissions).flatMap(c => writeFile(c))

  // preload
  load(dir)

}