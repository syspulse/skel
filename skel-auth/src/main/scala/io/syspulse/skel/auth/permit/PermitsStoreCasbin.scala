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

class PermitsStoreCasbin(implicit config:Config) extends PermitsStore {
  val log = Logger(s"${this}")

  val engine = new PermissionsCasbinFile(config.permissionsModel,config.permissionsPolicy)

  override def getEngine():Option[Permissions] = Some(engine)
  
  case class Perm(role:String,permissions:String)

  def toPerms(cbPerm:java.util.List[java.util.List[String]]):Seq[Perm] = 
    cbPerm.asScala.toSeq.foldLeft(Seq[Perm]()){ case(pp,p) => {
      pp :+ { p.asScala.toList.drop(1) match {
        case role :: perm :: Nil => Perm(role,perm)
      }}
    }}
  
  def all:Seq[Roles] = engine.enforcer.getAllSubjects().asScala.toList.flatMap( subj => {
    val roles = engine.enforcer.getRolesForUser(subj)
    val cbPerm = engine.enforcer.getPermissionsForUser(subj)
    log.info(s"subj=${subj}, roles=${roles}, permissions=${cbPerm}")
    try {
       if(Util.isUUID(subj)) {
          Some(Roles(
            UUID(subj),
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

  def getPermits():Seq[Permits] = engine.enforcer.getAllRoles().asScala.toList.flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(Permits(role, permissions = toPerms(cbPerm).map(_.permissions)))
    
  })

  def getPermits(role:String):Try[Permits] = engine.enforcer.getAllRoles().asScala.toList.filter(_ == role).flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(Permits(role, permissions = toPerms(cbPerm).map(_.permissions)))
  }) match {
    case h :: _ => Success(h)
    case Nil => Failure(new Exception(s"role not found: ${role}"))
  }

  // def getPermits(role:String):Seq[Permits] = engine.enforcer.getAllSubjects().asScala.toList.flatMap( role => {
  //   val roles = engine.enforcer.getRolesForUser(subj)    
  //   val cbPerm = engine.enforcer.getPermissionsForUser(subj)
  //   log.info(s"subj=${subj}, roles=${roles}, permissions=${cbPerm}")
  //   try {
  //      if(Util.isUUID(subj)) {
  //         Some(Permits(
  //           UUID(subj),
  //           permissions = toPermits(cbPerm)
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

  def +(r:Roles):Try[PermitsStore] = {
    log.info(s"add: ${r}")
    r.roles.foreach { role => 
      engine.enforcer.addRoleForUser(r.uid.toString, role)
    }    
    Success(this)
  }

  def del(uid:UUID):Try[PermitsStore] = { 
    log.info(s"del: ${uid}")
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    roles match {      
      case Nil => Failure(new Exception(s"not found: ${uid}"))
      case _ => 
        engine.enforcer.deleteUser(uid.toString)
        Success(this)
    }
  }

  def ?(uid:UUID):Try[Roles] = {
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    val cbPerm = engine.enforcer.getPermissionsForUser(uid.toString)
    roles match {
       case Nil => Failure(new Exception(s"not found: ${uid}"))
       case _ => 
        Success(Roles(
          uid,
          roles = roles.asScala.toSeq
        ))
    }
  }

  def update(uid:UUID,roles:Option[Seq[String]]):Try[Roles] = {
    ?(uid).map(p => modify(p,roles))
  }

  def addPermits(p:Permits):Try[PermitsStore] = Failure(new Exception(s"not supported"))
  def delPermits(role:String):Try[PermitsStore] = Failure(new Exception(s"not supported"))
}

