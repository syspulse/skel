package io.syspulse.skel.auth.store

import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.auth.store.AuthStore

class AuthStoreMem extends AuthStore {
  val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  var auths: Map[String,Auth] = Map()

  def all:Future[Seq[Auth]] = Future.successful(auths.values.toSeq)

  def findUser(uid:UUID):Future[Seq[Auth]] = {
    Future.successful(auths.values.filter(_.uid == Some(uid)).toSeq)
  }

  def size:Future[Long] = Future.successful(auths.size.toLong)

  def +(auth:Auth):Future[Auth] = {
    auths = auths + (auth.accessToken -> auth);
    log.info(s"Auth: ${auth}")
    Future.successful(auth)
  }

  def del(aid:String):Future[String] = {
    val sz = auths.size
    auths = auths - aid
    if(sz == auths.size) Future.failed(new Exception(s"not found: ${aid}")) else Future.successful(aid)
  }

  // def -(auth:Auth):Future[AuthStore] = {
  //   del(auth.accessToken)
  // }

  def ?(aid:String):Future[Auth] = auths.get(aid) match {
    case Some(a) => Future.successful(a)
    case None => Future.failed(new Exception(s"not found: ${aid}"))
  }

  def !(aid:String,accessToken:String,refreshToken:String,uid:Option[UUID] = None):Future[Auth] = ?(aid).flatMap { auth =>
    // remove old one and add updated
    val auth1 = auth.copy(
      accessToken = accessToken,
      refreshToken = Some(refreshToken),
      uid = if(uid.isDefined) uid else auth.uid)

    del(aid).flatMap(_ => this.+(auth1))
  }
}
