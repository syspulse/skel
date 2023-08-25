package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.auth.permissions.Permissions

trait PermitsStore extends Store[Roles,UUID] {
  
  def getKey(c: Roles): UUID = c.uid
  
  def +(c:Roles):Try[PermitsStore]

  // def !(client:Permits):Try[PermitsStore]
  //def -(c:Permits):Try[PermitsStore]
  
  def del(uid:UUID):Try[PermitsStore]
  def ?(uid:UUID):Try[Roles]
  def all:Seq[Roles]
  def size:Long

  def update(uid:UUID,roles:Option[Seq[String]]):Try[Roles]

  protected def modify(r:Roles,roles:Option[Seq[String]]=None):Roles = {    
    (for {
      c0 <- Some(r)
      c1 <- Some(if(roles.isDefined) c0.copy(roles = roles.get) else c0)      
    } yield c1).get    
  }

  protected def modifyPermits(p:Permits,permissions:Option[Seq[String]]=None):Permits = {    
    (for {
      c0 <- Some(p)
      c1 <- Some(if(permissions.isDefined) c0.copy(permissions = permissions.get) else c0)      
    } yield c1).get    
  }

  def getEngine():Option[Permissions] = None

  def getPermits():Seq[Permits]
  def getRoles():Seq[Roles] = all

  def addPermits(p:Permits):Try[PermitsStore]
  def addRoles(r:Roles):Try[PermitsStore] = `+`(r)

  def getPermits(role:String):Try[Permits]
  def getRoles(uid:UUID):Try[Roles] = `?`(uid)

  def delUser(uid:UUID):Try[PermitsStore] = del(uid)  
  def delPermits(role:String):Try[PermitsStore]
}

