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

class Permissions(modelFile:String,policyFile:String) {
  val log = Logger(s"${this}")

  log.info(s"model=${modelFile}, policy=${policyFile}")
  val enforcer = //new Enforcer(modelFile, policyFile);
    new Enforcer(
      {val m = new Model(); m.loadModelFromText(Util.loadFile(modelFile).toOption.getOrElse("")); m}, 
      new FileAdapter(new ByteArrayInputStream(Util.loadFile(policyFile).toOption.getOrElse("").getBytes(StandardCharsets.UTF_8)))
    )
  
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
    val act = "write";      // the operation that the user performs on the resource.

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
}

object Permissions {
  val log = Logger(s"${this}")

  val USER_NOBODY = UUID("00000000-0000-0000-0000-000000000000")
  val USER_ADMIN =  UUID("ffffffff-0000-0000-9000-000000000001")
  val USER_SERVICE =UUID("ffffffff-0000-0000-1000-000000000001")

  val ROLE_ADMIN = "admin"      
  val ROLE_SERVICE = "service"  // service (skel-enroll -> skel-notify)
  val ROLE_USER = "user"        // user (external -> API)
  val ROLE_NOBODY = "nobody"      // non-existing user (new user or anonymous -> API)

  val isGod = sys.props.contains("god") || sys.props.contains("GOD") || sys.env.contains("god") || sys.env.contains("GOD")

  def apply(modelFile:String,policyFile:String):Permissions = new Permissions(modelFile,policyFile)

  def apply():Permissions = 
    new Permissions("classpath:/default-permissions-model-rbac.conf","classpath:/default-permissions-policy-rbac.csv")
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
}
