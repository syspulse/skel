package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
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
  def all:Seq[PermitRole] = store.getPermit()
  def size:Long = store.getPermit().size
  override def +(r:PermitRole):Try[PermissionsStoreDir] = super.+(r).flatMap(_ => store.addPermit(r)).map(_ => this)

  override def del(r:String):Try[PermissionsStoreDir] = super.del(r).flatMap(_ => store.delPermit(r)).map(_ => this)
  override def ?(r:String):Try[PermitRole] = store.getPermit(r)

  def update(role:String,resources:Option[Seq[PermitResource]]):Try[PermitRole] =
    store.updatePermit(role,resources).flatMap(c => writeFile(c))

}

class PermitUserStoreDir(dir:String = "store/auth/rbac/users") extends StoreDir[PermitUser,UUID](dir) {
  val store = new PermitStoreMem

  def getKey(r: PermitUser): UUID = r.uid
  def toKey(id:String):UUID = UUID(id)
  def all:Seq[PermitUser] = store.all
  def size:Long = store.size
  override def +(c:PermitUser):Try[PermitUserStoreDir] = super.+(c).flatMap(_ => store.+(c)).map(_ => this)

  override def del(uid:UUID):Try[PermitUserStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:UUID):Try[PermitUser] = store.?(uid)

  def findPermitUserByXid(xid:String):Try[PermitUser] = store.findPermitUserByXid(xid)

  def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser] =
    store.update(uid,roles).flatMap(c => writeFile(c))

}

// Preload from file during start
class PermitStoreDir(dir:String = "store/auth/rbac") extends PermitStore {
  val permissionStore = new PermissionsStoreDir(dir + "/permissions")
  val userStore = new PermitUserStoreDir(dir + "/users")

  def getEngine():Option[Permissions] = permissionStore.store.getEngine()

  def all:Seq[PermitUser] = userStore.all
  def size:Long = userStore.size
  
  override def +(r:PermitUser):Try[PermitStoreDir] = userStore.+(r).map(_ => this)
  override def addPermit(p:PermitRole):Try[PermitStoreDir] = permissionStore.+(p).map(_ => this)

  override def del(uid:UUID):Try[PermitStoreDir] = userStore.del(uid).map(_ => this)
  override def ?(uid:UUID):Try[PermitUser] = userStore.?(uid)

  def findPermitUserByXid(xid:String):Try[PermitUser] = userStore.findPermitUserByXid(xid)

  override def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser] =
    userStore.update(uid,roles)

  def delPermit(role:String):Try[PermitStore] = permissionStore.del(role).map(_ => this)
  def getPermit(role:String):Try[PermitRole] = permissionStore.?(role)
  def getPermit():Seq[PermitRole] = permissionStore.all

  // preload
  permissionStore.load()
  userStore.load()

}