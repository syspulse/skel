package io.syspulse.skel.auth.cid

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class ClientStoreMem extends ClientStore {
  val log = Logger(s"${this}")

  var codes: Map[String,Client] = Map()

  def all:Seq[Client] = codes.values.toSeq

  def size:Long = codes.size

  def +(cid:Client):Try[ClientStore] = { 
    codes = codes + (cid.cid -> cid); Success(this)
  }

  def del(c:String):Try[ClientStore] = { 
    codes.get(c) match {
      case Some(auth) => { codes = codes - c; Success(this) }
      case None => Failure(new Exception(s"not found: ${c}"))
    }
  }

  def -(cid:Client):Try[ClientStore] = { 
    val sz = codes.size
    codes = codes - cid.cid;
    if(sz == codes.size) Failure(new Exception(s"not found: ${cid}")) else Success(this)
  }

  def ?(c:String):Try[Client] = codes.get(c) match {
    case Some(cid) => Success(cid)
    case None => Failure(new Exception(s"not found: ${c}"))
  }
}


