package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.auth.permissions.Permissions

trait PermitsStore extends Store[Permits,UUID] {
  
  def getKey(c: Permits): UUID = c.uid
  
  def +(c:Permits):Try[PermitsStore]

  // def !(client:Permits):Try[PermitsStore]
  //def -(c:Permits):Try[PermitsStore]
  
  def del(uid:UUID):Try[PermitsStore]
  def ?(uid:UUID):Try[Permits]
  def all:Seq[Permits]
  def size:Long

  def update(uid:UUID,roles:Option[Seq[String]]):Try[Permits]

  protected def modify(perm:Permits,roles:Option[Seq[String]]=None):Permits = {    
    (for {
      c0 <- Some(perm)
      c1 <- Some(if(roles.isDefined) c0.copy(roles = roles.get) else c0)      
    } yield c1).get    
  }

  def getEngine():Option[Permissions] = None
}

