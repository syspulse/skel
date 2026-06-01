package io.syspulse.skel.auth.permit

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future

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

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val engine = new PermissionsCasbinFile(config.permissionsModel,config.permissionsPolicy)

  override def getEngine():Option[Permissions] = Some(engine)

  case class Perm(role:String,resources:String)

  def toPerms(cbPerm:java.util.List[java.util.List[String]]):Seq[Perm] =
    cbPerm.asScala.toSeq.foldLeft(Seq[Perm]()){ case(pp,p) => {
      pp :+ { p.asScala.toList.drop(1) match {
        case role :: perm :: Nil => Perm(role,perm)
      }}
    }}

  def all:Future[Seq[PermitUser]] = Future.successful(engine.enforcer.getAllSubjects().asScala.toList.flatMap( subj => {
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
  }))

  def getPermit():Future[Seq[PermitRole]] = Future.successful(engine.enforcer.getAllRoles().asScala.toList.flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(PermitRole(role, resources = toPerms(cbPerm).map(p => PermitResource(p.resources,Seq()))))

  }))

  def getPermit(role:String):Future[PermitRole] = engine.enforcer.getAllRoles().asScala.toList.filter(_ == role).flatMap( role => {
    val cbPerm = engine.enforcer.getPermissionsForUser(role)
    log.info(s"role=${role}, permissions=${cbPerm}")
    Some(PermitRole(role, resources = toPerms(cbPerm).map(p => PermitResource(p.resources,Seq()))))
  }) match {
    case h :: _ => Future.successful(h)
    case Nil => Future.failed(new Exception(s"role not found: ${role}"))
  }

  def size:Future[Long] = all.map(_.size.toLong)

  def +(r:PermitUser):Future[PermitUser] = {
    log.info(s"add: ${r}")
    r.roles.foreach { role =>
      engine.enforcer.addRoleForUser(r.uid.toString, role)
    }
    Future.successful(r)
  }

  def del(uid:UUID):Future[UUID] = {
    log.info(s"del: ${uid}")
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    roles match {
      case Nil => Future.failed(new Exception(s"not found: ${uid}"))
      case _ =>
        engine.enforcer.deleteUser(uid.toString)
        Future.successful(uid)
    }
  }

  def ?(uid:UUID):Future[PermitUser] = {
    val roles = engine.enforcer.getRolesForUser(uid.toString)
    val cbPerm = engine.enforcer.getPermissionsForUser(uid.toString)
    roles match {
       case Nil => Future.failed(new Exception(s"not found: ${uid}"))
       case _ =>
        Future.successful(PermitUser(
          uid,
          roles = roles.asScala.toSeq,
          xid = ""
        ))
    }
  }

  def findPermitUserByXid(xid:String):Future[PermitUser] = Future.failed(new Exception(s"not supported"))

  def update(uid:UUID,roles:Option[Seq[String]]):Future[PermitUser] = {
    `?`(uid).map(p => modify(p,roles))
  }

  def addPermit(p:PermitRole):Future[PermitRole] = Future.failed(new Exception(s"not supported"))
  def delPermit(role:String):Future[String] = Future.failed(new Exception(s"not supported"))
}
