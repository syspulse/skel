package io.syspulse.skel.auth.permissions

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
import io.syspulse.skel.auth.permissions.casbin.PermissionsCasbinFile

trait Permissions {
  def isAdmin(uid:Option[UUID]):Boolean 

  def isService(uid:Option[UUID]):Boolean 

  def isUser(id:UUID,uid:Option[UUID]):Boolean

  def isAllowed(uid:Option[UUID],resource:String,action:String):Boolean 

  def hasRole(uid:Option[UUID],role:String):Boolean 
}

object Permissions {
  val log = Logger(s"${this}")

  val isGod = sys.props.contains("god") || sys.props.contains("GOD") || sys.env.contains("god") || sys.env.contains("GOD")

  def apply(permissionsModel:String,permissionsPolicy:String):Permissions = apply("casbin",Map(
    "modelFile" -> permissionsModel,
    "policyFile" -> permissionsPolicy
  ))

  def apply(engine:String,options:Map[String,String] = Map()):Permissions = engine.trim.toLowerCase match {
    case "casbin" => 
      val modelFile = options("modelFile")
      val policyFile = options("policyFile")
      new PermissionsCasbinFile(modelFile,policyFile)
    case "default" | "" => 
      throw new Exception(s"unknown engine: ${engine}")
  }

  def apply():Permissions = 
    apply("casbin",Map(
      "modelFile" -> "classpath:/default-permissions-model-rbac.conf",
      "policyFile" -> "classpath:/default-permissions-policy-rbac.csv"
    ))
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

  def isAllowed(resource:String,action:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    permissions.isAllowed(authn.getUser,resource,action)
  }

  def isAllowedRole(role:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    hasRole(role,authn)
  }

  def hasRole(role:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    // if roles are extracted from authn, it can be trusted and quickly validate
    val roles = authn.getRoles
    roles match {
      case Seq() => 
        permissions.hasRole(authn.getUser,role)
      case _ => 
        roles.contains(role)
    }
    
  }
}
