package io.syspulse.skel.auth.permit

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util
import io.jvm.uuid._
import io.syspulse.skel.auth.Config
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.rbac._

class PermitsStoreCasbin(implicit config:Config) extends PermitsStore {
  val log = Logger(s"${this}")

  val permissions = new PermissionsCasbinFile(config.permissionsModel,config.permissionsPolicy)
  
  def toPermits(cbPerm:java.util.List[java.util.List[String]]):Seq[Perm] = 
    cbPerm.asScala.toSeq.foldLeft(Seq[Perm]()){ case(pp,p) => {
      pp :+ { p.asScala.toList.drop(1) match {
        case role :: perm :: Nil => Perm(role,perm)
      }}
    }}
  
  def all:Seq[Permits] = permissions.enforcer.getAllSubjects().asScala.toList.flatMap( subj => {
    val roles = permissions.enforcer.getRolesForUser(subj)
    val cbPerm = permissions.enforcer.getPermissionsForUser(subj)
    log.info(s"subj=${subj}, roles=${roles}, permissions=${cbPerm}")
    try {
       if(Util.isUUID(subj)) {
          Some(Permits(
            UUID(subj),
            permissions = toPermits(cbPerm),
            roles = roles.asScala.toSeq
          ))
       } else {
          // not UUID, don't bother
          None
       }
    } catch {
      case e:Exception =>
        None
    }
  })

  def size:Long = all.size

  def +(p:Permits):Try[PermitsStore] = {
    log.info(s"add: ${p}")
    p.roles.foreach { role => 
      permissions.enforcer.addRoleForUser(p.uid.toString, role)
    }
    
    Success(this)
  }

  def del(uid:UUID):Try[PermitsStore] = { 
    log.info(s"del: ${uid}")
    val roles = permissions.enforcer.getRolesForUser(uid.toString)
    roles match {      
      case Nil => Failure(new Exception(s"not found: ${uid}"))
      case _ => 
        permissions.enforcer.deleteUser(uid.toString)
        Success(this)
    }
  }

  def ?(uid:UUID):Try[Permits] = {
    val roles = permissions.enforcer.getRolesForUser(uid.toString)
    val cbPerm = permissions.enforcer.getPermissionsForUser(uid.toString)
    roles match {
       case Nil => Failure(new Exception(s"not found: ${uid}"))
       case _ => 
        Success(Permits(
          uid,
          permissions = toPermits(cbPerm),
          roles = roles.asScala.toSeq
        ))
    }
  }

  def update(uid:UUID,permissions:Option[Seq[Perm]]):Try[Permits] = {
    ?(uid).map(p => modify(p,permissions))
  }
}

