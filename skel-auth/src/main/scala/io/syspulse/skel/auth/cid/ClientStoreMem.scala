package io.syspulse.skel.auth.cid

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ClientStoreMem extends ClientStore {
  val log = Logger(s"${this}")

  val defaultClients = { val cid="eaf9642f76195dca7529c0589e6d6259"; cid -> Client(Some(cid),Some("vn5digFyJVZCIVLExNo_Hynz0zDxEUDRlu5FHB9Qvj8")) }
  var clients: Map[String,Client] = Map() + defaultClients

  def all:Seq[Client] = clients.values.toSeq

  def size:Long = clients.size

  def +(cid:Client):Try[ClientStore] = { 
    clients = clients + (cid.cid -> cid); Success(this)
  }

  def del(c:String):Try[ClientStore] = { 
    clients.get(c) match {
      case Some(auth) => { clients = clients - c; Success(this) }
      case None => Failure(new Exception(s"not found: ${c}"))
    }
  }

  def -(cid:Client):Try[ClientStore] = { 
    val sz = clients.size
    clients = clients - cid.cid;
    if(sz == clients.size) Failure(new Exception(s"not found: ${cid}")) else Success(this)
  }

  def ?(c:String):Try[Client] = clients.get(c) match {
    case Some(cid) => Success(cid)
    case None => Failure(new Exception(s"not found: ${c}"))
  }
}


