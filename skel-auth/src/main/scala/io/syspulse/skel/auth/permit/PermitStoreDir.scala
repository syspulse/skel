package io.syspulse.skel.auth.permit

import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable
import io.jvm.uuid._

import com.typesafe.scalalogging.Logger

import os._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.auth.permit.PermitStoreMem
import io.syspulse.skel.store.StoreDir
import PermitJson._
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitJson
import io.syspulse.skel.auth.permit.PermitStore
import io.syspulse.skel.auth.permissions.Permissions

class PermissionsStoreDir(dir:String = "store/auth/rbac/permissions") extends StoreDir[PermitRole,String](dir) {
  val store = new PermitStoreMem

  def getKey(p: PermitRole): String = p.role
  def toKey(id:String):String = id
  def all:Future[Seq[PermitRole]] = store.getPermit()
  def size:Future[Long] = store.getPermit().map(_.size.toLong)
  override def +(r:PermitRole):Future[PermitRole] = super.+(r).flatMap(_ => store.addPermit(r))

  override def del(r:String):Future[String] = super.del(r).flatMap(_ => store.delPermit(r))
  override def ?(r:String):Future[PermitRole] = store.getPermit(r)

  def update(role:String,resources:Option[Seq[PermitResource]]):Future[PermitRole] =
    store.updatePermit(role,resources).flatMap(c => Future.fromTry(writeFile(c)))

}

class PermitUserStoreDir(dir:String = "store/auth/rbac/users") extends StoreDir[PermitUser,UUID](dir) {
  val store = new PermitStoreMem

  def getKey(r: PermitUser): UUID = r.uid
  def toKey(id:String):UUID = UUID(id)
  def all:Future[Seq[PermitUser]] = store.all
  def size:Future[Long] = store.size
  override def +(c:PermitUser):Future[PermitUser] = super.+(c).flatMap(_ => store.+(c))

  override def del(uid:UUID):Future[UUID] = super.del(uid).flatMap(_ => store.del(uid))
  override def ?(uid:UUID):Future[PermitUser] = store.?(uid)

  def findPermitUserByXid(xid:String):Future[PermitUser] = store.findPermitUserByXid(xid)

  def update(uid:UUID,roles:Option[Seq[String]]):Future[PermitUser] =
    store.update(uid,roles).flatMap(c => Future.fromTry(writeFile(c)))

}

// Preload from file during start
class PermitStoreDir(dir:String = "store/auth/rbac") extends PermitStore {
  val permissionStore = new PermissionsStoreDir(dir + "/permissions")
  val userStore = new PermitUserStoreDir(dir + "/users")

  def getEngine():Option[Permissions] = permissionStore.store.getEngine()

  def all:Future[Seq[PermitUser]] = userStore.all
  def size:Future[Long] = userStore.size

  override def +(r:PermitUser):Future[PermitUser] = userStore.+(r)
  override def addPermit(p:PermitRole):Future[PermitRole] = permissionStore.+(p)

  override def del(uid:UUID):Future[UUID] = userStore.del(uid)
  override def ?(uid:UUID):Future[PermitUser] = userStore.?(uid)

  def findPermitUserByXid(xid:String):Future[PermitUser] = userStore.findPermitUserByXid(xid)

  override def update(uid:UUID,roles:Option[Seq[String]]):Future[PermitUser] =
    userStore.update(uid,roles)

  def delPermit(role:String):Future[String] = permissionStore.del(role)
  def getPermit(role:String):Future[PermitRole] = permissionStore.?(role)
  def getPermit():Future[Seq[PermitRole]] = permissionStore.all

  // preload
  permissionStore.load()
  userStore.load()

}
