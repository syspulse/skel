package io.syspulse.skel.auth.permit

import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}

trait PermitStore extends Store[PermitUser,UUID] {

  def getKey(c: PermitUser): UUID = c.uid

  def +(c:PermitUser):Future[PermitUser]

  // def !(client:PermitRole):Future[PermitStore]
  //def -(c:PermitRole):Future[PermitStore]

  def del(uid:UUID):Future[UUID]
  def ?(uid:UUID):Future[PermitUser]
  def all:Future[Seq[PermitUser]]
  def size:Future[Long]

  def update(uid:UUID,roles:Option[Seq[String]]):Future[PermitUser]

  protected def modify(r:PermitUser,roles:Option[Seq[String]]=None):PermitUser = {
    (for {
      c0 <- Some(r)
      c1 <- Some(if(roles.isDefined) c0.copy(roles = roles.get) else c0)
    } yield c1).get
  }

  protected def modifyPermit(p:PermitRole,resources:Option[Seq[PermitResource]]=None):PermitRole = {
    (for {
      c0 <- Some(p)
      c1 <- Some(if(resources.isDefined) c0.copy(resources = resources.get) else c0)
    } yield c1).get
  }

  def getEngine():Option[Permissions]

  def getPermit():Future[Seq[PermitRole]]
  def getPermitUser():Future[Seq[PermitUser]] = all

  def addPermit(p:PermitRole):Future[PermitRole]
  def addPermitUser(r:PermitUser):Future[PermitUser] = `+`(r)

  def getPermit(role:String):Future[PermitRole]
  def getPermitUser(uid:UUID):Future[PermitUser] = `?`(uid)
  def findPermitUserByXid(xid:String):Future[PermitUser]

  def delUser(uid:UUID):Future[UUID] = del(uid)
  def delPermit(role:String):Future[String]
}
