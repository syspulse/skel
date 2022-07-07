package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import org.casbin.jcasbin.main.Enforcer
import io.syspulse.skel.auth.Authenticated

class Permissions(modelFile:String,policyFile:String) {
  val log = Logger(s"${this}")

  val enforcer = new Enforcer(modelFile, policyFile);
  
  def isAdmin(uid:Option[UUID]):Boolean = {
    log.info(s"god=${Permissions.isGod}: uid=${uid}")

    if(Permissions.isGod) return true
    if(!uid.isDefined) return false

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "write";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }

  def isUser(uid:Option[UUID]):Boolean = {
    log.info(s"god=${Permissions.isGod}: uid=${uid}")

    if(Permissions.isGod) return true
    if(!uid.isDefined) return false

    val sub = uid.get.toString; // the user that wants to access a resource.
    val obj = "api";        // the resource that is going to be accessed.
    val act = "read";      // the operation that the user performs on the resource.

    enforcer.enforce(sub, obj, act)
  }
}

object Permissions {
  val log = Logger(s"${this}")
  val isGod = System.getProperty("god") != null

  def apply(modelFile:String,policyFile:String):Permissions = new Permissions(modelFile,policyFile)

  def apply():Permissions = new Permissions("conf/permissions-model-rbac.conf","conf/permissions-policy-rbac.csv")

  def isAdmin(authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    val uid = authn.getUser
    permissions.isAdmin(uid)
  }

  def isUser(id:UUID,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    val uid = authn.getUser
    
    log.info(s"god=${isGod}: id=${id}: uid=${uid}")

    if(!isGod && Some(id) != uid) return {
      log.error(s"god=${isGod}: id=${id} != uid=${uid}: ")
      false
    }

    permissions.isUser(uid)
  }
}
