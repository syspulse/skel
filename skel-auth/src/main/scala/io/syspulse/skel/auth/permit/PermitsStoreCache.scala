package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions

trait PermitsStoreCache extends PermitsStore {
  val log = Logger(s"${this}")

  // default clinet for quick prototyping
  val defaultPermits = { 
    DefaultPermissions.USER_ADMIN -> Permits(DefaultPermissions.USER_ADMIN, permissions = Seq(Perm("*","write")),roles = Seq("admin"))
  }

  var permits: Map[UUID,Permits] = Map() + defaultPermits

  def all:Seq[Permits] = permits.values.toSeq

  def size:Long = permits.size

  def +(p:Permits):Try[PermitsStore] = {
    log.info(s"add: ${p}")
    permits = permits + (p.uid -> p); Success(this)
  }

  def del(uid:UUID):Try[PermitsStore] = { 
    log.info(s"del: ${uid}")
    permits.get(uid) match {
      case Some(auth) => { permits = permits - uid; Success(this) }
      case None => Failure(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Try[Permits] = permits.get(uid) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${uid}"))
  }

  def update(uid:UUID,Permits:Option[Seq[Perm]]):Try[Permits] = {
    ?(uid).map(p => modify(p,Permits))
  }
}

