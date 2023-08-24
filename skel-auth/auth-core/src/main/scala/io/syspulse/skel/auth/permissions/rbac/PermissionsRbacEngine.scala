package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated

import io.syspulse.skel.auth.permissions.Permissions

trait PermissionsRbacEngine {
  val log = Logger(s"${this}")

  def getUserRoles(uid:UUID):Seq[Role]
  def getRolePermissions(role:Role):Seq[ResourcePermission]

  def hasRole(uid:Option[UUID],role:String):Boolean = {
    if(!uid.isDefined)
      return false

    val roles = getUserRoles(uid.get)
    roles.find(r => r.n == role).isDefined
  }
    
  def permit(uid:Option[UUID],reqRes:Resource,reqPerm:Permission):Boolean = {    
    if(!uid.isDefined)
      return false

    val roles = getUserRoles(uid.get)
    val rp:Map[Resource,Seq[Permission]] = roles
      .map(role => {
        val rps = getRolePermissions(role)
        rps.map( rp => {
          rp.r -> rp.pp
        }).toMap
      })
      .foldLeft(Seq[(Resource,Seq[Permission])]())((l,r) => l ++ r.toSeq)
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).toSeq.flatten.distinct)
      .toMap
    
    val res = rp.get(reqRes)
    if(! res.isDefined)
      return false

    log.info(s"req=[${reqRes}/${reqPerm} ---> ${res}]")
    res.get.contains(reqPerm)      
   
  }

}
