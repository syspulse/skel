package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}

trait PermitStore extends Store[PermitUser,UUID] {
  
  def getKey(c: PermitUser): UUID = c.uid
  
  def +(c:PermitUser):Try[PermitStore]

  // def !(client:PermitRole):Try[PermitStore]
  //def -(c:PermitRole):Try[PermitStore]
  
  def del(uid:UUID):Try[PermitStore]
  def ?(uid:UUID):Try[PermitUser]
  def all:Seq[PermitUser]
  def size:Long

  def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser]

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

  def getEngine():Option[Permissions] = None

  def getPermit():Seq[PermitRole]
  def getPermitUser():Seq[PermitUser] = all

  def addPermit(p:PermitRole):Try[PermitStore]
  def addPermitUser(r:PermitUser):Try[PermitStore] = `+`(r)

  def getPermit(role:String):Try[PermitRole]
  def getPermitUser(uid:UUID):Try[PermitUser] = `?`(uid)

  def delUser(uid:UUID):Try[PermitStore] = del(uid)  
  def delPermit(role:String):Try[PermitStore]
}

