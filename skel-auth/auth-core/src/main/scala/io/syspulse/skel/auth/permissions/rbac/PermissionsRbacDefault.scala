package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.DefaultPermissions

object DemoRbac {
  val roles = Map(
    Roles.ROLE_ADMIN -> Seq( ResourcePermission(ResourceAll(),Seq(PermissionAll())) ),
    Roles.ROLE_SERVICE -> Seq( ResourcePermission(ResourceApi(),Seq(PermissionWrite(),PermissionRead())) ),
    Roles.ROLE_USER -> Seq( 
      ResourcePermission(Resource("00000000-0000-0000-1000-000000000001"),Seq(PermissionWrite())),
      ResourcePermission(Resource("notify"),Seq(PermissionRead())),
      ResourcePermission(ResourceApi(),Seq(PermissionRead()))
    ),
    Role("data") -> Seq(
      ResourcePermission(ResourceOf("data"),Seq(PermissionWrite())),
    )
  )

  val users = Map(
    {val u = DefaultPermissions.USER_ADMIN; u -> Seq(Roles.ROLE_ADMIN)},
    {val u = DefaultPermissions.USER_SERVICE; u -> Seq(Roles.ROLE_SERVICE)},
    {val u = UUID("00000000-0000-0000-1000-000000000001"); u -> Seq(Roles.ROLE_USER)},
    {val u = UUID("00000000-0000-0000-1000-000000000002"); u -> Seq(Roles.ROLE_USER,Role("data"))}
  )
}

// ================================================== Demo =====
class PermissionsRbacEngineDemo() extends PermissionsRbacEngine {
  
  def getUserRoles(uid:UUID):Seq[Role] = {
    DemoRbac.users.get(uid).getOrElse(Seq())
  }

  def getRolePermissions(role:Role):Seq[ResourcePermission] = {
    DemoRbac.roles.get(role).getOrElse(Seq())
  }
}

class PermissionsRbacDemo() extends PermissionsRbac {  
  val engine = new PermissionsRbacEngineDemo()
  log.info(s"RBAC: ${engine}: ${DemoRbac.users}")
}

// =================================================== Default  =====
class PermissionsRbacEngineDefault() extends PermissionsRbacEngine {
  
  def getUserRoles(uid:UUID):Seq[Role] = Seq()
  def getRolePermissions(role:Role):Seq[ResourcePermission] = Seq()

  // role can be matched since it is signed 
  override def hasRole(authn:Authenticated,role:String):Boolean = {
    authn.getUser.isDefined && authn.getRoles.contains(role)
  }  
}

class PermissionsRbacDefault() extends PermissionsRbac {  
  val engine = new PermissionsRbacEngineDefault()
  log.info(s"RBAC: ${engine}")
}
