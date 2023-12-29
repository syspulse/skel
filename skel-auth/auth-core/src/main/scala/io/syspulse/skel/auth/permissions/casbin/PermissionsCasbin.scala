package io.syspulse.skel.auth.permissions.casbin

import scala.jdk.CollectionConverters._
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
  
  def isAdmin(authn:Authenticated):Boolean = {
    log.info(s"admin: GOD=${Permissions.isGod}: authn(${authn})")

    if(Permissions.isGod) 
      return true
    if(!authn.getUser.isDefined) {
      log.error(s"admin: GOD=${Permissions.isGod}: authn(${authn})")
      return false
    }

    val sub = authn.getUser.get.toString; // the user that wants to access a resource.
    val obj = "*";        // the resource that is going to be accessed.
    val act = "write";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isService(authn:Authenticated):Boolean = {
    log.info(s"service: GOD=${Permissions.isGod}: authn(${authn})")

    if(Permissions.isGod) 
      return true
    if(! authn.getUser.isDefined) {
      log.error(s"service: GOD=${Permissions.isGod}: authn(${authn})")
      return false
    }

    val sub = authn.getUser.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "write";      // write across the system

    enforcer.enforce(sub, obj, act)
  }

  def isUser(id:UUID,authn:Authenticated):Boolean = {
    log.info(s"user: GOD=${Permissions.isGod}: id(${id}): authn(${authn})")

    if(Permissions.isGod) 
      return true

    if(Some(id) != authn.getUser) {
      log.error(s"user: GOD=${Permissions.isGod}: id(${id}): authn(${authn})")
      return false
    }

    if(!authn.getUser.isDefined) 
      return false
    
    // val sub = uid.get.toString; // the user that wants to access a resource.
    // val obj = "api";        // the resource that is going to be accessed.
    // val act = "read";      // the operation that the user performs on the resource.

    // enforcer.enforce(sub, obj, act)
    return true
  }

  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean = {
    
    if(! authn.getUser.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: authn($authn), resource=${resource}:${action}")
      return false
    }

    val sub = authn.getUser.get.toString; // the user that wants to access a resource.
    val obj = resource;        // the resource that is going to be accessed.
    val act = action;      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def hasRole(authn:Authenticated,role:String):Boolean = {
    if(! authn.getUser.isDefined) {
      log.error(s"allowed: GOD=${Permissions.isGod}: authn($authn), role=${role}")
      return false
    }

    val r = enforcer.getRolesForUser(authn.getUser.get.toString)
    r.contains(role)    
  }
}
