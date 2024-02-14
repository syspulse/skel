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
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDemo

trait Permissions {
  def isAdmin(authn:Authenticated):Boolean 

  def isService(authn:Authenticated):Boolean 

  def isUser(id:UUID,authn:Authenticated):Boolean

  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean 

  def hasRole(authn:Authenticated,role:String):Boolean 
}

object Permissions {
  val log = Logger(s"${this}")

  val isGod = sys.props.contains("god") || sys.props.contains("GOD") || sys.env.contains("god") || sys.env.contains("GOD")

  def apply(permissionsModel:String,permissionsPolicy:String):Permissions = apply("casbin",Map(
    "modelFile" -> permissionsModel,
    "policyFile" -> permissionsPolicy
  ))

  def apply(engine:String,options:Map[String,String] = Map()):Permissions = engine.trim.toLowerCase.split("://").toList match {
    case "casbin" :: Nil | "casbin://" :: Nil => 
      val modelFile = options("modelFile")
      val policyFile = options("policyFile")
      new PermissionsCasbinFile(modelFile,policyFile)
    case "default" :: _  =>
      new PermissionsRbacDefault()
    case "rbac" :: _ |  "mem" :: _ | "cache" :: _  => 
      new PermissionsRbacDefault()
    case "dir" :: _ =>
      new PermissionsRbacDefault()
    case "demo" :: _  =>
      // demo preconfigured users and roles
      new PermissionsRbacDemo()
    case className :: Nil =>
      // try to spawn custom Permissions Engine
      this.getClass.getClassLoader.loadClass(className).getDeclaredConstructor().newInstance().asInstanceOf[Permissions]
  }

  def apply():Permissions = 
    apply("demo")    

  def isAdmin(authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    permissions.isAdmin(authn)
  }

  def isService(authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    permissions.isService(authn)
  }

  def isUser(id:UUID,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    permissions.isUser(id,authn)
  }

  def isAllowed(resource:String,action:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    permissions.isAllowed(authn,resource,action)
  }

  def isAllowedRole(role:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {    
    hasRole(role,authn)
  }

  def hasRole(role:String,authn:Authenticated)(implicit permissions:Permissions):Boolean = {
    // if roles are extracted from authn, it can be trusted and quickly validate
    val roles = authn.getRoles
    roles match {
      case Seq() => 
        permissions.hasRole(authn,role)
      case _ => 
        roles.contains(role)
    }
    
  }
}
