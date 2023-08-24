package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.syspulse.skel.auth.permissions.Permissions

trait PermissionsRbac extends Permissions {
  val log = Logger(s"${this}")
  
  val engine:PermissionsRbacEngine

  def isAdmin(uid:Option[UUID]):Boolean = {
    log.info(s"admin: GOD=${Permissions.isGod}: uid(${uid})")

    if(Permissions.isGod) return true
    if(!uid.isDefined) {
      log.error(s"admin: GOD=${Permissions.isGod}: uid($uid)")
      return false
    }
    
    engine.permit(uid,ResourceAll(),PermissionAll())
  }

  def isService(uid:Option[UUID]):Boolean = {
    log.info(s"service: GOD=${Permissions.isGod}: uid(${uid})")

    if(Permissions.isGod) return true
    if(!uid.isDefined) {
      log.error(s"service: GOD=${Permissions.isGod}: uid($uid)")
      return false
    }
    
    engine.permit(uid,ResourceApi(),PermissionWrite())
  }

  def isUser(id:UUID,uid:Option[UUID]):Boolean = {
    log.info(s"user: GOD=${Permissions.isGod}: id(${id}): uid=${uid}")

    if(Permissions.isGod) return true

    if(Some(id) != uid) return {
      log.error(s"user: GOD=${Permissions.isGod}: id(${id}): uid($uid)")
      false
    }
    if(!uid.isDefined) 
      return false

    return true;    
  }

  def isAllowed(uid:Option[UUID],resource:String,action:String):Boolean = {
    if(!uid.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: uid($uid), resource=${resource}:${action}")
      return false
    }

    engine.permit(uid,ResourceOf(resource),PermissionOf(action))
  }

  def hasRole(uid:Option[UUID],role:String):Boolean = {
    if(!uid.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: uid($uid), role=${role}")
      return false
    }

    engine.hasRole(uid,role)
  }
}
