package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import org.casbin.jcasbin.main.Enforcer
import io.syspulse.skel.auth.Authenticated
import org.casbin.jcasbin.model.Model
import org.casbin.jcasbin.persist.file_adapter.FileAdapter
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

trait Permissions {
  val log = Logger(s"${this}")

  val enforcer:Enforcer
  
  def isAdmin(uid:Option[UUID]):Boolean = {
    log.info(s"admin: GOD=${Permissions.isGod}: uid(${uid})")

    if(Permissions.isGod) return true
    if(!uid.isDefined) {
      log.error(s"admin: GOD=${Permissions.isGod}: uid($uid)")
      return false
    }

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "write";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isService(uid:Option[UUID]):Boolean = {
    log.info(s"service: GOD=${Permissions.isGod}: uid(${uid})")

    if(Permissions.isGod) return true
    if(!uid.isDefined) {
      log.error(s"service: GOD=${Permissions.isGod}: uid($uid)")
      return false
    }

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "write";      // write across the system

    enforcer.enforce(sub, obj, act)
  }

  def isUser(id:UUID,uid:Option[UUID]):Boolean = {
    log.info(s"user: GOD=${Permissions.isGod}: id(${id}): uid=${uid}")

    if(Permissions.isGod) return true

    if(Some(id) != uid) return {
      log.error(s"user: GOD=${Permissions.isGod}: id(${id}): uid($uid)")
      false
    }
    if(!uid.isDefined) return false

    return true;

    // ATTENTION: DON'T ENFORCE FOR NOW !
    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "read";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isAllowed(uid:Option[UUID],role:String,action:String):Boolean = {
    if(!uid.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: uid($uid), role=${role}:${action}")
      return false
    }

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = role;        // the resource that is going to be accessed.
    val act = action;      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }
}

object Permissions {
  val log = Logger(s"${this}")

  val isGod = sys.props.contains("god") || sys.props.contains("GOD") || sys.env.contains("god") || sys.env.contains("GOD")

  def apply(modelFile:String,policyFile:String):Permissions = 
    new PermissionsFile(modelFile,policyFile)

  def apply():Permissions = 
    new PermissionsFile("classpath:/default-permissions-model-rbac.conf","classpath:/default-permissions-policy-rbac.csv")
    //new Permissions("conf/permissions-model-rbac.conf","conf/permissions-policy-rbac.csv")

  def isAdmin(authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    val uid = authn.getUser
    permissions.isAdmin(uid)
  }

  def isService(authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    val uid = authn.getUser
    permissions.isService(uid)
  }

  def isUser(id:UUID,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    val uid = authn.getUser
    permissions.isUser(id,uid)
  }

  def isAllowed(role:String,action:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    permissions.isAllowed(authn.getUser,role,action)
  }
}
