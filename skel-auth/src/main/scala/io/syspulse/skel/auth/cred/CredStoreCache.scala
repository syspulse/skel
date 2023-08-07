package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.Permissions

trait CredStoreCache extends CredStore {
  val log = Logger(s"${this}")

  // default clinet for quick prototyping
  val defaultCreds = { 
    val cid="eaf9642f76195dca7529c0589e6d6259"; 
    cid -> Cred(Some(cid),Some("vn5digFyJVZCIVLExNo_Hynz0zDxEUDRlu5FHB9Qvj8"),age= 3600L*24 *365, 
                uid = UUID("00000000-0000-0000-1000-000000000001"))
  }

  var clients: Map[String,Cred] = Map() + defaultCreds

  def all:Seq[Cred] = clients.values.toSeq

  def size:Long = clients.size

  def +(c:Cred):Try[CredStore] = {
    log.info(s"add: ${c}")
    clients = clients + (c.cid -> c); Success(this)
  }

  def del(cid:String):Try[CredStore] = { 
    log.info(s"del: ${cid}")
    clients.get(cid) match {
      case Some(auth) => { clients = clients - cid; Success(this) }
      case None => Failure(new Exception(s"not found: ${cid}"))
    }
  }

  def ?(id:String):Try[Cred] = clients.get(id) match {
    case Some(c) => Success(c)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def update(id:String,secret:Option[String]=None,name:Option[String]=None,expire:Option[Long] = None):Try[Cred] = {
    ?(id).map(c => modify(c,secret,name,expire))
  }
}

