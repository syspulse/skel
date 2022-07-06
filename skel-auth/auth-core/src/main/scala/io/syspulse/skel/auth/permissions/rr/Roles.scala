package io.syspulse.skel.auth.permissions.rr

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util

case class Role(n:String)

object Roles {

  val ADMIN_ROLE = Role("admin")
  val USER_ROLE = Role("user")

}

case class UserRoles(uid:UUID,roles:List[Role] = List())

object RolesStore {

  val roles = Map(
    Roles.ADMIN_ROLE -> List( ResourcePermission(ResourceAll(),Seq(PermissionAll())) ),
    Roles.USER_ROLE -> List( ResourcePermission(ResourceData(),Seq(PermissionRead())) )
  )

  val default = Map(
    {val u = UUID("1fffffff-ffff-ffff-ffff-000000000001"); u -> UserRoles(u,List(Roles.ADMIN_ROLE))},
    {val u = UUID("00000000-0000-0000-1000-000000000003"); u -> UserRoles(u,List(Roles.USER_ROLE))}
  )
  
  def hasAdminPermissions(uid: UUID): Boolean = {
    val userRole = RolesStore.default.get(uid)
    if(! userRole.isDefined) return false

    val rr = userRole.get.roles

    val resourcePermissions = rr.map(rn => roles.get(rn).getOrElse(List())).flatten.distinct
    if(resourcePermissions.size == 0) return false

    resourcePermissions.filter( rp => rp.r match {
      case ResourceAll() | ResourceData() if (rp.pp.contains(PermissionAll()))=> true
      case _ => false
    }).size > 0
  }

}