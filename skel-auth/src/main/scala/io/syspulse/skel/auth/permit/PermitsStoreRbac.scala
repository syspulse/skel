package io.syspulse.skel.auth.permit

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.permissions.rbac.DefaultRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault

class PermitsStoreRbac(ext:String="") extends PermitsStoreMem {

  override val permissions = ext match {
    case ""  => new PermissionsRbacDefault()
  }

}

