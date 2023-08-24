package io.syspulse.skel.auth.permissions.casbin

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

import io.syspulse.skel.auth.permissions.Permissions

trait PermissionsCasbin extends Permissions {
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
    val obj = "*";        // the resource that is going to be accessed.
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

    
    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "read";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isAllowed(uid:Option[UUID],resource:String,action:String):Boolean = {
    if(!uid.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: uid($uid), resource=${resource}:${action}")
      return false
    }

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = resource;        // the resource that is going to be accessed.
    val act = action;      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isRole(uid:Option[UUID],roles:Seq[String],role:String):Boolean = {
    if(!uid.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: uid($uid), roles=${roles}")
      return false
    }

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = role;        // the resource that is going to be accessed.
    val act = "write";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }
}
