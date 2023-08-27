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
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitStore

class PermitStoreMem extends PermitStore {
  val log = Logger(s"${this}")

  val permissions:Permissions = new PermissionsRbacDefault() //new PermissionsRbacFile(config.rbac)

  def getEngine():Option[Permissions] = Some(permissions)

  val defaultUsers = { DefaultRbac.users.map(u =>
      u._1 -> PermitUser( 
        uid = u._1,
        roles = u._2.map(_.n)
      )
    ).toMap
  }

  val defaultPermit = { 
    DefaultRbac.roles.map(r =>
      r._1.n -> PermitRole( 
        role = r._1.n,
        resources = r._2.map(rp => PermitResource(rp.r.get,rp.pp.map(p => p.get)))
      )
    ).toMap
  }

  var roles: Map[UUID,PermitUser] = Map() ++ defaultUsers
  var permits: Map[String,PermitRole] = Map() ++ defaultPermit

  def all:Seq[PermitUser] = roles.values.toSeq

  def size:Long = roles.size

  def +(p:PermitUser):Try[PermitStore] = {
    log.info(s"add: ${p}")
    roles = roles + (p.uid -> p)
    Success(this)
  }

  def del(uid:UUID):Try[PermitStore] = { 
    log.info(s"del: ${uid}")
    roles.get(uid) match {
      case Some(auth) => { roles = roles - uid; Success(this) }
      case None => Failure(new Exception(s"not found: ${uid}"))
    }
  }

  def ?(uid:UUID):Try[PermitUser] = roles.get(uid) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${uid}"))
  }

  def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser] = {
    ?(uid).map(p => modify(p,roles))
  }

  def updatePermit(role:String,resources:Option[Seq[PermitResource]]):Try[PermitRole] = {
    getPermit(role).map(p => modifyPermit(p,resources))
  }

  def addPermit(p:PermitRole):Try[PermitStore] = {
    permits = permits + (p.role -> p)
    Success(this)
  }

  def delPermit(role:String):Try[PermitStore] = { 
    log.info(s"del: ${role}")
    permits.get(role) match {
      case Some(r) => { permits = permits - role; Success(this) }
      case None => Failure(new Exception(s"not found: ${role}"))
    }
  }

  def getPermit():Seq[PermitRole] = permits.values.toSeq

  def getPermit(role:String):Try[PermitRole] = permits.get(role) match {
    case Some(r) => Success(r)
    case None => Failure(new Exception(s"not found: ${role}"))
  }

}

