package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.permissions.rbac.DefaultRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.Permissions

class PermitsStoreMem extends PermitsStore {
  val log = Logger(s"${this}")

  val permissions:Permissions = new PermissionsRbacDefault() //new PermissionsRbacFile(config.rbac)

  val defaultUsers = { DefaultRbac.users.map(u =>
      u._1 -> Roles( 
        uid = u._1,
        roles = u._2.map(_.n)
      )
    ).toMap
  }

  val defaultPermits = { 
    DefaultRbac.roles.map(r =>
      r._1.n -> Permits( 
        role = r._1.n,
        permissions = r._2.map(rp => rp.pp).flatten.map(p => p.get)
      )
    ).toMap
  }

  var roles: Map[UUID,Roles] = Map() ++ defaultUsers
  var permits: Map[String,Permits] = Map() ++ defaultPermits

  def all:Seq[Roles] = roles.values.toSeq

  def size:Long = roles.size

  def +(p:Roles):Try[PermitsStore] = {
    log.info(s"add: ${p}")
    roles = roles + (p.uid -> p)
    Success(this)
  }

  def del(uid:UUID):Try[PermitsStore] = { 
    log.info(s"del: ${uid}")
    roles.get(uid) match {
      case Some(auth) => { roles = roles - uid; Success(this) }
      case None => Failure(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Try[Roles] = roles.get(uid) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${uid}"))
  }

  def update(uid:UUID,roles:Option[Seq[String]]):Try[Roles] = {
    ?(uid).map(p => modify(p,roles))
  }

  def updatePermits(role:String,permissions:Option[Seq[String]]):Try[Permits] = {
    getPermits(role).map(p => modifyPermits(p,permissions))
  }

  def addPermits(p:Permits):Try[PermitsStore] = {
    permits = permits + (p.role -> p)
    Success(this)
  }

  def delPermits(role:String):Try[PermitsStore] = { 
    log.info(s"del: ${role}")
    permits.get(role) match {
      case Some(r) => { permits = permits - role; Success(this) }
      case None => Failure(new Exception(s"not found: ${role}"))
    }
  }

  def getPermits():Seq[Permits] = permits.values.toSeq

  def getPermits(role:String):Try[Permits] = permits.get(role) match {
    case Some(r) => Success(r)
    case None => Failure(new Exception(s"not found: ${role}"))
  }

}

