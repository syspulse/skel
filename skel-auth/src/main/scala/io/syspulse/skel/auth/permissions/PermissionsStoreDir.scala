package io.syspulse.skel.auth.permissions

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.auth.permissions.PermissionsStoreMem
import io.syspulse.skel.store.StoreDir
import PermissionsJson._

// Preload from file during start
class PermissionsStoreDir(dir:String = "store/permissions/") extends StoreDir[Permissions,UUID](dir) with PermissionsStore {
  val store = new PermissionsStoreMem

  def toKey(id:String):UUID = UUID(id)
  def all:Seq[Permissions] = store.all
  def size:Long = store.size
  override def +(c:Permissions):Try[PermissionsStoreDir] = super.+(c).flatMap(_ => store.+(c)).map(_ => this)

  override def del(uid:UUID):Try[PermissionsStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:UUID):Try[Permissions] = store.?(uid)

  override def update(uid:UUID,permissions:Option[Seq[String]]):Try[Permissions] =
    store.update(uid,permissions).flatMap(c => writeFile(c))

  // preload
  load(dir)

}