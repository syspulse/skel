package io.syspulse.skel.auth.permissions

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import scala.util.Success

import io.syspulse.skel.util.Util
import pdi.jwt.JwtAlgorithm
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.DefaultPermissions
import io.syspulse.skel.auth.AuthenticatedUser

class PermissionSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  
  "Permission (default)" should {

    implicit val permissions = Permissions()
    
    "validate Admin User/Account" in {
      val uid = DefaultPermissions.USER_ADMIN
      val authn = AuthenticatedUser(uid,roles = Seq("admin"))
      Permissions.isAdmin(authn) should === (true)
    }

    "validate Service Account/User and not Admin" in {
      val uid = DefaultPermissions.USER_SERVICE
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAdmin(authn) should === (false)
      Permissions.isService(authn) should === (true)
    }

    "not validate logged User as Admin and Service" in {
      val uid = UUID("00000000-0000-0000-1000-000000000001")
      val authn = AuthenticatedUser(uid,roles = Seq("user"))
      Permissions.isAdmin(authn) should === (false)
      Permissions.isService(authn) should === (false)
    }

  }
}
