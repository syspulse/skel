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
import io.syspulse.skel.auth.permissions.casbin._
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitStore

class PermitStoreCasbin(implicit config:Config) extends PermitStore {
  val log = Logger(s"${this}")

  val engine = new PermissionsCasbinFile(config.permissionsModel,config.permissionsPolicy)

  override def getEngine():Option[Permissions] = Some(engine)
  
  case class Perm(role:String,resources:String)

  def toPerms(cbPerm:java.util.List[java.util.List[String]]):Seq[Perm] = 
    cbPerm.asScala.toSeq.foldLeft(Seq[Perm]()){ case(pp,p) => {
      pp :+ { p.asScala.toList.drop(1) match {
        case role :: perm :: Nil => Perm(role,perm)
      }}
    }}
  
  def all:Seq[PermitUser] = engine.enforcer.getAllSubjects().asScala.toList.flatMap( subj => {
    val roles = engine.enforcer.getRolesForUser(subj)
    val cbPerm = engine.enforcer.getPermissionsForUser(subj)
    log.info(s"subj=${subj}, roles=${roles}, permissions=${cbPerm}")
    try {
       if(Util.isUUID(subj)) {
          Some(PermitUser(
            UUID(subj),
            roles = roles.asScala.toSeq,
            xid = ""
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

  def getPermit():Seq[PermitRole] = engine.enforcer.getAllRoles().asScala.toList.flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(PermitRole(role, resources = toPerms(cbPerm).map(p => PermitResource(p.resources,Seq()))))
    
  })

  def getPermit(role:String):Try[PermitRole] = engine.enforcer.getAllRoles().asScala.toList.filter(_ == role).flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(PermitRole(role, resources = toPerms(cbPerm).map(p => PermitResource(p.resources,Seq()))))
  }) match {
    case h :: _ => Success(h)
    case Nil => Failure(new Exception(s"role not found: ${role}"))
  }

  // def getPermit(role:String):Seq[PermitRole] = engine.enforcer.getAllSubjects().asScala.toList.flatMap( role => {
  //   val roles = engine.enforcer.getRolesForUser(subj)    
  //   val cbPerm = engine.enforcer.getPermissionsForUser(subj)
  //   log.info(s"subj=${subj}, roles=${roles}, permissions=${cbPerm}")
  //   try {
  //      if(Util.isUUID(subj)) {
  //         Some(PermitRole(
  //           UUID(subj),
  //           permissions = toPermit(cbPerm)
  //         ))
  //      } else {
  //         // not UUID, don't bother
  //         None
  //      }
  //   } catch {
  //     case e:Exception =>
  //       None
  //   }
  // })

  def size:Long = all.size

  def +(r:PermitUser):Try[PermitStore] = {
    log.info(s"add: ${r}")
    r.roles.foreach { role => 
      engine.enforcer.addRoleForUser(r.uid.toString, role)
    }    
    Success(this)
  }

  def del(uid:UUID):Try[PermitStore] = { 
    log.info(s"del: ${uid}")
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    roles match {      
      case Nil => Failure(new Exception(s"not found: ${uid}"))
      case _ => 
        engine.enforcer.deleteUser(uid.toString)
        Success(this)
    }
  }

  def ?(uid:UUID):Try[PermitUser] = {
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    val cbPerm = engine.enforcer.getPermissionsForUser(uid.toString)
    roles match {
       case Nil => Failure(new Exception(s"not found: ${uid}"))
       case _ => 
        Success(PermitUser(
          uid,
          roles = roles.asScala.toSeq,
          xid = ""
        ))
    }
  }

  def findPermitUserByXid(xid:String):Try[PermitUser] = Failure(new Exception(s"not supported"))

  def update(uid:UUID,roles:Option[Seq[String]]):Try[PermitUser] = {
    ?(uid).map(p => modify(p,roles))
  }

  def addPermit(p:PermitRole):Try[PermitStore] = Failure(new Exception(s"not supported"))
  def delPermit(role:String):Try[PermitStore] = Failure(new Exception(s"not supported"))
}

