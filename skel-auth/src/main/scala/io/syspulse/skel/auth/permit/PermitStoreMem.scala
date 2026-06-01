package io.syspulse.skel.auth.permit

import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions

import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitStore

class PermitStoreMem extends PermitStore {
  val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val permissions:Permissions = new PermissionsRbacDefault() //new PermissionsRbacFile(config.rbac)

  def getEngine():Option[Permissions] = Some(permissions)

  var users: Map[UUID,PermitUser] = Map()
  var permits: Map[String,PermitRole] = Map()

  def all:Future[Seq[PermitUser]] = Future.successful(users.values.toSeq)

  def size:Future[Long] = Future.successful(users.size.toLong)

  def +(p:PermitUser):Future[PermitUser] = {
    log.info(s"add: ${p}")
    users = users + (p.uid -> p)
    Future.successful(p)
  }

  def del(uid:UUID):Future[UUID] = {
    log.info(s"del: ${uid}")
    users.get(uid) match {
      case Some(u) => { users = users - uid; Future.successful(uid) }
      case None => Future.failed(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Future[PermitUser] = users.get(uid) match {
    case Some(p) => Future.successful(p)
    case None => Future.failed(new Exception(s"not found: ${uid}"))
  }

  def findPermitUserByXid(xid:String):Future[PermitUser] =
    users.values.find( u => u.xid == xid) match {
      case Some(p) => Future.successful(p)
      case None => Future.failed(new Exception(s"not found: ${xid}"))
  }

  def update(uid:UUID,roles:Option[Seq[String]]):Future[PermitUser] = {
    ?(uid).map(p => modify(p,roles))
  }

  def updatePermit(role:String,resources:Option[Seq[PermitResource]]):Future[PermitRole] = {
    getPermit(role).map(p => modifyPermit(p,resources))
  }

  def addPermit(p:PermitRole):Future[PermitRole] = {
    permits = permits + (p.role -> p)
    Future.successful(p)
  }

  def delPermit(role:String):Future[String] = {
    log.info(s"del: ${role}")
    permits.get(role) match {
      case Some(r) => { permits = permits - role; Future.successful(role) }
      case None => Future.failed(new Exception(s"not found: ${role}"))
    }
  }

  def getPermit():Future[Seq[PermitRole]] = Future.successful(permits.values.toSeq)

  def getPermit(role:String):Future[PermitRole] = permits.get(role) match {
    case Some(r) => Future.successful(r)
    case None => Future.failed(new Exception(s"not found: ${role}"))
  }

}
