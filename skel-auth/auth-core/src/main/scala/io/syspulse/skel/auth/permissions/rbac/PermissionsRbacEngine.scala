package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated

import io.syspulse.skel.auth.permissions.Permissions

trait PermissionsRbacEngine {
  val log = Logger(s"${this}")

  def getUserRoles(uid:UUID):Seq[Role]  
  def getUserRoles(authnRoles:Seq[String]):Seq[Role] = authnRoles.map(r => Role(r))  
  def getRolePermissions(role:Role):Seq[ResourcePermission]

  def hasRole(authn:Authenticated,role:String):Boolean = {
    if(!authn.getUser.isDefined)
      return false

    val roles = { 
      val roles = getUserRoles(authn.getRoles)
      // val roles = getUserRoles(authn.getUser.get)    
      if(roles.isEmpty) {
        log.warn(s"roles=${roles}: assume 'user'")
        Seq(Role("user"))        
      } else
        roles
    }

    roles.find(r => r.n == role).isDefined
  }
    
  def permit(authn:Authenticated,reqRes:Resource,reqPerm:Permission):Boolean = {
    if(!authn.getUser.isDefined)
      return false

    val roles = { 
      val roles = getUserRoles(authn.getRoles)
      // val roles = getUserRoles(authn.getUser.get)    
      if(roles.isEmpty) {
        log.warn(s"req=[${reqRes}/${reqPerm}: roles=${roles}: assume 'user'")
        Seq(Role("user"))        
      } else
        roles
    }      

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
        
    val res = rp.get(reqRes)//.orElse(rp.get(ResourceAll()))

    log.info(s"req=[${reqRes}/${reqPerm}: roles=${roles}: ${rp}] => ${res}")

    if(! res.isDefined)
      return false

    res.get.contains(reqPerm)
   
  }

}
