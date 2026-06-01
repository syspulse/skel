package io.syspulse.skel.auth.cred

import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.Permissions

trait CredStoreCache extends CredStore {
  val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  // default client for quick prototyping
  val defaultCreds = {
    val cid="eaf9642f76195dca7529c0589e6d6259";
    cid -> Cred(Some(cid),Some("vn5digFyJVZCIVLExNo_Hynz0zDxEUDRlu5FHB9Qvj8"),age= 3600L*24 *365,
                uid = UUID("00000000-0000-0000-1000-000000000001"))
  }

  var clients: Map[String,Cred] = Map() + defaultCreds

  def all:Future[Seq[Cred]] = Future.successful(clients.values.toSeq)

  def size:Future[Long] = Future.successful(clients.size.toLong)

  def +(c:Cred):Future[Cred] = {
    log.info(s"add: ${c}")
    clients = clients + (c.cid -> c); Future.successful(c)
  }

  def del(cid:String):Future[String] = {
    log.info(s"del: ${cid}")
    clients.get(cid) match {
      case Some(cred) => { clients = clients - cid; Future.successful(cid) }
      case None => Future.failed(new Exception(s"not found: ${cid}"))
    }
  }

  def ?(id:String):Future[Cred] = clients.get(id) match {
    case Some(c) => Future.successful(c)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def update(id:String,secret:Option[String]=None,name:Option[String]=None,expire:Option[Long] = None):Future[Cred] = {
    ?(id).map(c => modify(c,secret,name,expire))
  }
}
