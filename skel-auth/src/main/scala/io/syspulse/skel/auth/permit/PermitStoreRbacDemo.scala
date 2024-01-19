package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.permissions.rbac.DemoRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitStore

class PermitStoreRbacDemo extends PermitStoreMem {  

  users = { DemoRbac.users.map(u =>
      u._1 -> PermitUser( 
        uid = u._1,
        roles = u._2.map(_.n),
        xid = if(u._1 == UUID("00000000-0000-0000-1000-000000000001")) "101436214428674710353" else 
              if(u._1 == UUID("00000000-0000-0000-1000-000000000002")) "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1" else 
              ""
      )
    ).toMap
  }

  permits = { 
    DemoRbac.roles.map(r =>
      r._1.n -> PermitRole( 
        role = r._1.n,
        resources = r._2.map(rp => PermitResource(rp.r.get,rp.pp.map(p => p.get)))
      )
    ).toMap
  }

}

