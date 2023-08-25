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

  def isAdmin(authn:Authenticated):Boolean = {
    log.info(s"admin: GOD=${Permissions.isGod}: authn(${authn})")

    if(Permissions.isGod) 
      return true

    if(! authn.getUser.isDefined) {
      log.error(s"admin: GOD=${Permissions.isGod}: authn($authn)")
      return false
    }
    
    //engine.permit(authn,ResourceAll(),PermissionAll())
    engine.hasRole(authn,"admin")
  }

  def isService(authn:Authenticated):Boolean = {
    log.info(s"service: GOD=${Permissions.isGod}: authn(${authn})")

    if(Permissions.isGod) 
      return true
    
    if(!authn.getUser.isDefined) {
      log.error(s"service: GOD=${Permissions.isGod}: authn($authn)")
      return false
    }
    
    //engine.permit(authn,ResourceApi(),PermissionWrite())
    engine.hasRole(authn,"service")
  }

  def isUser(id:UUID,authn:Authenticated):Boolean = {
    log.info(s"user: GOD=${Permissions.isGod}: id(${id}): authn=${authn}")

    if(Permissions.isGod) 
      return true

    if(Some(id) != authn.getUser) {
      log.error(s"user: GOD=${Permissions.isGod}: id(${id}): authn($authn)")
      return false
    }

    if(!authn.getUser.isDefined) 
      return false

    engine.hasRole(authn,"user")
  }

  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean = {
    if(!authn.getUser.isDefined) {
      log.error(s"not allowed: GOD=${Permissions.isGod}: authn($authn), resource=${resource}:${action}")
      return false
    }

    engine.permit(authn,ResourceOf(resource),PermissionOf(action))
  }

  def hasRole(authn:Authenticated,role:String):Boolean = {
    if(!authn.getUser.isDefined) {
      log.error(s"not allowed: GOD=${Permissions.isGod}: authn($authn), role=${role}")
      return false
    }

    engine.hasRole(authn,role)
  }
}
