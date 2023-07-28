package io.syspulse.skel.auth.permissions

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait PermissionsStore extends Store[Permissions,UUID] {
  
  def getKey(c: Permissions): UUID = c.uid
  
  def +(c:Permissions):Try[PermissionsStore]

  // def !(client:Permissions):Try[PermissionsStore]
  //def -(c:Permissions):Try[PermissionsStore]
  
  def del(uid:UUID):Try[PermissionsStore]
  def ?(uid:UUID):Try[Permissions]
  def all:Seq[Permissions]
  def size:Long

  def update(uid:UUID,permissions:Option[Seq[String]]):Try[Permissions]

  protected def modify(perm:Permissions,permissions:Option[Seq[String]]=None):Permissions = {    
    (for {
      c0 <- Some(perm)
      c1 <- Some(if(permissions.isDefined) c0.copy(permissions = permissions.get) else c0)      
    } yield c1).get    
  }
}

