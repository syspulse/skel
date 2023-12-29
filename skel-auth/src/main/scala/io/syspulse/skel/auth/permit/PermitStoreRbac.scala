package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions

import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permit.PermitStoreMem
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDemo

class PermitStoreRbac(ext:String="") extends PermitStoreMem {

  override val permissions = ext match {
    case "" | "default"  => new PermissionsRbacDefault()
    case "demo"  => new PermissionsRbacDemo()
  }

}

