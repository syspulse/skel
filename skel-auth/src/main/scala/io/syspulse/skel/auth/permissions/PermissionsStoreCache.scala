package io.syspulse.skel.auth.permissions

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

trait PermissionsStoreCache extends PermissionsStore {
  val log = Logger(s"${this}")

  // default clinet for quick prototyping
  val defaultPermissions = { 
    DefaultPermissions.USER_ADMIN -> Permissions(DefaultPermissions.USER_ADMIN,Seq("role-1"))
  }

  var permissions: Map[UUID,Permissions] = Map() + defaultPermissions

  def all:Seq[Permissions] = permissions.values.toSeq

  def size:Long = permissions.size

  def +(p:Permissions):Try[PermissionsStore] = {
    log.info(s"add: ${p}")
    permissions = permissions + (p.uid -> p); Success(this)
  }

  def del(uid:UUID):Try[PermissionsStore] = { 
    log.info(s"del: ${uid}")
    permissions.get(uid) match {
      case Some(auth) => { permissions = permissions - uid; Success(this) }
      case None => Failure(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Try[Permissions] = permissions.get(uid) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${uid}"))
  }

  def update(uid:UUID,permissions:Option[Seq[String]]):Try[Permissions] = {
    ?(uid).map(p => modify(p,permissions))
  }
}

