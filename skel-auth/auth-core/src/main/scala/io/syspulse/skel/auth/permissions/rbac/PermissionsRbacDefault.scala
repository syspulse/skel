package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.syspulse.skel.auth.permissions.Permissions

object DefaultRbac {
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
    {val u = UUID("ffffffff-0000-0000-9000-000000000001"); u -> Seq(Roles.ROLE_ADMIN)},
    {val u = UUID("eeeeeeee-0000-0000-1000-000000000001"); u -> Seq(Roles.ROLE_SERVICE)},
    {val u = UUID("00000000-0000-0000-1000-000000000001"); u -> Seq(Roles.ROLE_USER)},
    {val u = UUID("00000000-0000-0000-1000-000000000002"); u -> Seq(Roles.ROLE_USER,Role("data"))}
  )
}

class PermissionsRbacEngineDefault() extends PermissionsRbacEngine {
  
  def getUserRoles(uid:UUID):Seq[Role] = {
    DefaultRbac.users.get(uid).getOrElse(Seq())
  }

  def getRolePermissions(role:Role):Seq[ResourcePermission] = {
    DefaultRbac.roles.get(role).getOrElse(Seq())
  }
}

class PermissionsRbacDefault() extends PermissionsRbac {  
  val engine = new PermissionsRbacEngineDefault()
  
}
