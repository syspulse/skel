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

class PermissionsStoreDir(dir:String = "store/auth/rbac/permissions") extends StoreDir[Permits,String](dir) {
  val store = new PermitsStoreMem
  
  def getKey(p: Permits): String = p.role
  def toKey(id:String):String = id
  def all:Seq[Permits] = store.getPermits()
  def size:Long = store.getPermits().size
  override def +(r:Permits):Try[PermissionsStoreDir] = super.+(r).flatMap(_ => store.addPermits(r)).map(_ => this)

  override def del(r:String):Try[PermissionsStoreDir] = super.del(r).flatMap(_ => store.delPermits(r)).map(_ => this)
  override def ?(r:String):Try[Permits] = store.getPermits(r)

  def update(role:String,permissions:Option[Seq[String]]):Try[Permits] =
    store.updatePermits(role,permissions).flatMap(c => writeFile(c))

}

class RolesStoreDir(dir:String = "store/auth/rbac/roles") extends StoreDir[Roles,UUID](dir) {
  val store = new PermitsStoreMem

  def getKey(r: Roles): UUID = r.uid
  def toKey(id:String):UUID = UUID(id)
  def all:Seq[Roles] = store.all
  def size:Long = store.size
  override def +(c:Roles):Try[RolesStoreDir] = super.+(c).flatMap(_ => store.+(c)).map(_ => this)

  override def del(uid:UUID):Try[RolesStoreDir] = super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  override def ?(uid:UUID):Try[Roles] = store.?(uid)

  def update(uid:UUID,roles:Option[Seq[String]]):Try[Roles] =
    store.update(uid,roles).flatMap(c => writeFile(c))

}

// Preload from file during start
class PermitsStoreDir(dir:String = "store/auth/rbac") extends PermitsStore {
  val permitsStore = new PermissionsStoreDir(dir + "/permissions")
  val rolesStore = new RolesStoreDir(dir + "/roles")

  def all:Seq[Roles] = rolesStore.all
  def size:Long = rolesStore.size

  override def +(r:Roles):Try[PermitsStoreDir] = rolesStore.+(r).map(_ => this)
  override def addPermits(p:Permits):Try[PermitsStoreDir] = permitsStore.+(p).map(_ => this)

  override def del(uid:UUID):Try[PermitsStoreDir] = rolesStore.del(uid).map(_ => this)
  override def ?(uid:UUID):Try[Roles] = rolesStore.?(uid)

  override def update(uid:UUID,roles:Option[Seq[String]]):Try[Roles] =
    rolesStore.update(uid,roles)

  def delPermits(role:String):Try[PermitsStore] = permitsStore.del(role).map(_ => this)
  def getPermits(role:String):Try[Permits] = permitsStore.?(role)
  def getPermits():Seq[Permits] = permitsStore.all

  // preload
  permitsStore.load(dir)
  rolesStore.load(dir)

}