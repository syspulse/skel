package io.syspulse.skel.auth.cred

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

trait CredStoreCache extends CredStore {
  // default clinet for quick prototyping
  val defaultCreds = { val cid="eaf9642f76195dca7529c0589e6d6259"; cid -> Cred(Some(cid),Some("vn5digFyJVZCIVLExNo_Hynz0zDxEUDRlu5FHB9Qvj8"),age=3600L*24 *365) }

  var clients: Map[String,Cred] = Map() + defaultCreds

  def all:Seq[Cred] = clients.values.toSeq

  def size:Long = clients.size

  def +(cid:Cred):Try[CredStore] = { 
    clients = clients + (cid.cid -> cid); Success(this)
  }

  def del(c:String):Try[CredStore] = { 
    clients.get(c) match {
      case Some(auth) => { clients = clients - c; Success(this) }
      case None => Failure(new Exception(s"not found: ${c}"))
    }
  }

  // override def -(cid:Cred):Try[CredStore] = { 
  //   val sz = clients.size
  //   clients = clients - cid.cid;
  //   if(sz == clients.size) Failure(new Exception(s"not found: ${cid}")) else Success(this)
  // }

  def ?(c:String):Try[Cred] = clients.get(c) match {
    case Some(cid) => Success(cid)
    case None => Failure(new Exception(s"not found: ${c}"))
  }
}

