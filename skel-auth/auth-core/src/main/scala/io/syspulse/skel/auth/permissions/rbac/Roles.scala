package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

case class Role(n:String)

object Roles {

  val ROLE_ADMIN = Role("admin")
  val ROLE_SERVICE = Role("service")
  val ROLE_USER = Role("user")
}

case class UserRoles(uid:UUID,roles:List[Role] = List())

// object RolesStore {

//   val roles = Map(
//     Roles.ROLE_ADMIN -> List( ResourcePermission(ResourceAll(),Seq(PermissionAll())) ),
//     Roles.ROLE_SERVICE -> List( ResourcePermission(ResourceApi(),Seq(PermissionWrite(),PermissionRead())) ),
//     Roles.ROLE_USER -> List( ResourcePermission(ResourceData(),Seq(PermissionRead())) )
//   )

//   val default = Map(
//     {val u = UUID("ffffffff-0000-0000-9000-000000000001"); u -> UserRoles(u,List(Roles.ROLE_ADMIN))},
//     {val u = UUID("eeeeeeee-0000-0000-1000-000000000001"); u -> UserRoles(u,List(Roles.ROLE_SERVICE))},
//     {val u = UUID("00000000-0000-0000-1000-000000000001"); u -> UserRoles(u,List(Roles.ROLE_USER))}
//   )
  
//   def hasAdminPermissions(uid: UUID): Boolean = {
//     val userRole = RolesStore.default.get(uid)
//     if(! userRole.isDefined) return false

//     val rr = userRole.get.roles

//     val resourcePermissions = rr.map(rn => roles.get(rn).getOrElse(List())).flatten.distinct
//     if(resourcePermissions.size == 0) return false

//     resourcePermissions.filter( rp => rp.r match {
//       case ResourceAll() | ResourceData() if (rp.pp.contains(PermissionAll()))=> true
//       case _ => false
//     }).size > 0
//   }
// }