package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.permissions.rbac.DefaultRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitStore

class PermitStoreMem extends PermitStore {
  val log = Logger(s"${this}")

  val permissions:Permissions = new PermissionsRbacDefault() //new PermissionsRbacFile(config.rbac)

  def getEngine():Option[Permissions] = Some(permissions)
  
  var users: Map[UUID,PermitUser] = Map()
  var permits: Map[String,PermitRole] = Map()

  def all:Seq[PermitUser] = users.values.toSeq

  def size:Long = users.size

  def +(p:PermitUser):Try[PermitStore] = {
    log.info(s"add: ${p}")
    users = users + (p.uid -> p)
    Success(this)
  }

  def del(uid:UUID):Try[PermitStore] = { 
    log.info(s"del: ${uid}")
    users.get(uid) match {
      case Some(auth) => { users = users - uid; Success(this) }
      case None => Failure(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Try[PermitUser] = users.get(uid) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${uid}"))
  }

  def findPermitUserByXid(xid:String):Try[PermitUser] = 
    users.values.find( u => u.xid == xid) match {
      case Some(p) => Success(p)
      case None => Failure(new Exception(s"not found: ${xid}"))  
  }

  def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser] = {
    ?(uid).map(p => modify(p,roles))
  }

  def updatePermit(role:String,resources:Option[Seq[PermitResource]]):Try[PermitRole] = {
    getPermit(role).map(p => modifyPermit(p,resources))
  }

  def addPermit(p:PermitRole):Try[PermitStore] = {
    permits = permits + (p.role -> p)
    Success(this)
  }

  def delPermit(role:String):Try[PermitStore] = { 
    log.info(s"del: ${role}")
    permits.get(role) match {
      case Some(r) => { permits = permits - role; Success(this) }
      case None => Failure(new Exception(s"not found: ${role}"))
    }
  }

  def getPermit():Seq[PermitRole] = permits.values.toSeq

  def getPermit(role:String):Try[PermitRole] = permits.get(role) match {
    case Some(r) => Success(r)
    case None => Failure(new Exception(s"not found: ${role}"))
  }

}

