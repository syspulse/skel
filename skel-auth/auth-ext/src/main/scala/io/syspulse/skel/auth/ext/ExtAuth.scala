package io.syspulse.skel.auth.ext

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers
import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.util.Util

object ExtAuth {
  val log = Logger(this.getClass)

  val EXT_ROLES_PATH = ".groups[]"
  val EXT_ADMIN_ROLE = "extractor-admin"
  val EXT_SERVICE_ROLE = "extractor-service"
  val EXT_USER_ROLE = "extractor-user"
  val EXT_VIEWER_ROLE = "extractor-viewer"

  val EXT_PERMISSION_READ = "read"
  val EXT_PERMISSION_WRITE = "write"
  val EXT_PERMISSION_DELETE = "delete"
  val EXT_PERMISSION_UPDATE = "update"

  val EXT_ROLES_READ = Seq(
    EXT_ADMIN_ROLE, 
    EXT_SERVICE_ROLE,
    EXT_USER_ROLE,
    EXT_VIEWER_ROLE
  )
  val EXT_ROLES_WRITE = Seq(
    EXT_ADMIN_ROLE,
    EXT_SERVICE_ROLE,
    EXT_USER_ROLE
  )
  val EXT_ROLES_DELETE = Seq(
    EXT_ADMIN_ROLE,
    EXT_SERVICE_ROLE,
    EXT_USER_ROLE
  )
  val EXT_ROLES_UPDATE = Seq(
    EXT_ADMIN_ROLE,
    EXT_SERVICE_ROLE,
    EXT_USER_ROLE
  )

  def getOwner(authn:Authenticated,ownerAttr:String = "tenantId"):Option[String] = {    
    val t = authn.getToken    
    if(!t.isDefined) return None
        
    val json = ujson.read(t.get.claim.content)
    
    val oid =  json.obj.get(ownerAttr).map(_.str) //json.obj(ownerAttr).strOpt

    log.debug(s"[JWT] '${json}' attr=${ownerAttr}, oid=$oid")

    oid match {
      case Some(tid) => Some(tid)
      case None => 
        log.debug(s"[JWT] missing attr: '${ownerAttr}'")
        None
    }
  }

  def isAdminRole(authn:Authenticated,rolePath:String,adminRole:String):Boolean = {
    isRole(authn,rolePath,adminRole)
  }

  def isServiceRole(authn:Authenticated,rolePath:String,serviceRole:String):Boolean = {
    isRole(authn,rolePath,serviceRole)
  }

  def isRole(authn:Authenticated,rolePath:String,role:String):Boolean =
    isRole(authn,rolePath,Seq(role))

  protected def findRole(authn:Authenticated,rolePath:String,roles:Seq[String],op:(Seq[String],Seq[String]) => Boolean):Boolean = {
    val t = authn.getToken
    if(!t.isDefined) return false

    val claims = t.get.claim.content
    
    Util.walkJson(claims,rolePath) match {
      case Success(rr) => 
        //println(s"====> claims: ${claims}, rolePath: ${rolePath}, roles: ${roles}, rr: ${rr}")

        val rolesJwt = rr.map(r => r.toString().stripPrefix("\"").stripSuffix("\""))
        val rolesFound = rolesJwt.intersect(roles)

        op(rolesFound,roles)

      case Failure(e) => 
        log.debug(s"JWT Role attribute not found: ${roles}: ${e.getMessage()}")
        false
    }
  }

  def isRole(authn:Authenticated,rolePath:String,roles:Seq[String]):Boolean = {    
    findRole(authn,rolePath,roles,(rolesFound,roles) => rolesFound.size == roles.size)
  }

  def hasRole(authn:Authenticated,rolePath:String,roles:Seq[String]):Boolean = {    
    findRole(authn,rolePath,roles,(rolesFound,roles) => rolesFound.size > 0)
  }
}


class ExtRbacStrict(
  adminRole:String = ExtAuth.EXT_ADMIN_ROLE,
  serviceRole:String = ExtAuth.EXT_SERVICE_ROLE,
  rolePath:String = ExtAuth.EXT_ROLES_PATH) extends Permissions {  
 
  def isAdmin(authn:Authenticated):Boolean = Permissions.isGod || ExtAuth.isAdminRole(authn,rolePath,adminRole)
  def isService(authn:Authenticated):Boolean = Permissions.isGod || ExtAuth.isServiceRole(authn,rolePath,serviceRole)
  // not supported mapping to User, only Service Account
  def isUser(id:UUID,authn:Authenticated):Boolean = false
  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean = false
  def hasRole(authn:Authenticated,role:String):Boolean = Permissions.isGod || ExtAuth.hasRole(authn,rolePath,Seq(role))
}

// ==== User Permissions ==============================================================================================================
class ExtRbacUser(
  adminRole:String = ExtAuth.EXT_ADMIN_ROLE,
  serviceRole:String = ExtAuth.EXT_SERVICE_ROLE,
  rolePath:String = ExtAuth.EXT_ROLES_PATH) extends Permissions {
 
  def isAdmin(authn:Authenticated):Boolean = Permissions.isGod || ExtAuth.isAdminRole(authn,rolePath,adminRole)
  def isService(authn:Authenticated):Boolean = Permissions.isGod || ExtAuth.isServiceRole(authn,rolePath,serviceRole)
  
  def isUser(id:UUID,authn:Authenticated):Boolean = 
    ExtAuth.isRole(authn,rolePath,Seq(ExtAuth.EXT_USER_ROLE,ExtAuth.EXT_VIEWER_ROLE))

  def isAllowed(authn:Authenticated,resource:String,action:String):Boolean = {
    action match {
      case ExtAuth.EXT_PERMISSION_READ =>
        ExtAuth.hasRole(authn,rolePath,ExtAuth.EXT_ROLES_READ)
      case ExtAuth.EXT_PERMISSION_WRITE =>
        ExtAuth.hasRole(authn,rolePath,ExtAuth.EXT_ROLES_WRITE)
      case ExtAuth.EXT_PERMISSION_DELETE =>
        ExtAuth.hasRole(authn,rolePath,ExtAuth.EXT_ROLES_DELETE)
      case ExtAuth.EXT_PERMISSION_UPDATE =>
        ExtAuth.hasRole(authn,rolePath,ExtAuth.EXT_ROLES_UPDATE)
      case _ => false
    }    
  }

  def hasRole(authn:Authenticated,role:String):Boolean = Permissions.isGod || ExtAuth.hasRole(authn,rolePath,Seq(role))
}
