package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CredStore extends Store[Cred,String] {
  
  def getKey(c: Cred): String = c.cid
  
  def +(c:Cred):Try[CredStore]
  // def !(client:Cred):Try[CredStore]
  //def -(c:Cred):Try[CredStore]
  def del(cid:String):Try[CredStore]
  def ?(cid:String):Try[Cred]
  def all:Seq[Cred]
  def size:Long

  def update(id:String,secret:Option[String]=None,name:Option[String]=None,expire:Option[Long] = None):Try[Cred]

  protected def modify(cred:Cred, secret:Option[String]=None,name:Option[String]=None, age:Option[Long] = None):Cred = {    
    (for {
      c0 <- Some(cred)
      c1 <- Some(if(secret.isDefined) c0.copy(secret = secret.get) else c0)
      c2 <- Some(if(name.isDefined) c1.copy(name = name.get) else c1)
      c3 <- Some(if(age.isDefined) c2.copy(expire = System.currentTimeMillis + age.get * 1000L ) else c2)
    } yield c3).get    
  }
}

