package io.syspulse.skel.auth.cred

import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CredStore extends Store[Cred,String] {

  def getKey(c: Cred): String = c.cid

  def +(c:Cred):Future[Cred]
  // def !(client:Cred):Future[Cred]
  //def -(c:Cred):Future[Cred]
  def del(cid:String):Future[String]
  def ?(cid:String):Future[Cred]
  def all:Future[Seq[Cred]]
  def size:Future[Long]

  def update(id:String,secret:Option[String]=None,name:Option[String]=None,expire:Option[Long] = None):Future[Cred]

  protected def modify(cred:Cred, secret:Option[String]=None,name:Option[String]=None, age:Option[Long] = None):Cred = {
    (for {
      c0 <- Some(cred)
      c1 <- Some(if(secret.isDefined) c0.copy(secret = secret.get) else c0)
      c2 <- Some(if(name.isDefined) c1.copy(name = name.get) else c1)
      c3 <- Some(if(age.isDefined) c2.copy(expire = System.currentTimeMillis + age.get * 1000L ) else c2)
    } yield c3).get
  }
}
