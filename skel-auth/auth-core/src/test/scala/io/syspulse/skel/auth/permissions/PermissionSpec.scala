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
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbac
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDefault
import io.syspulse.skel.auth.permissions.rbac.PermissionsRbacDemo

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

    "allow 'api' write for Service Account" in {
      val uid = DefaultPermissions.USER_SERVICE
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAllowed("api","write",authn) should === (true)      
    }

    "not allow 'api' write for User Account" in {
      val uid = UUID("00000000-0000-0000-1000-000000000001")
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAllowed("api","write",authn) should === (false)
      Permissions.isAllowed("api","read",authn) should === (true)
    }

    "allow Service User with `service` role" in {
      val uid = DefaultPermissions.USER_SERVICE
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAllowedRole("service",authn) should === (true)      
    }

  }

  "PermissionRbac" should {

    implicit val permissions = new PermissionsRbacDemo()
    
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

    "allow 'api' write for Service Account" in {
      val uid = DefaultPermissions.USER_SERVICE
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAllowed("api","write",authn) should === (true)      
    }

    "not allow 'api' write for User Account" in {
      val uid = UUID("00000000-0000-0000-1000-000000000001")
      val authn = AuthenticatedUser(uid,roles = Seq("user"))
      Permissions.isAllowed("api","write",authn) should === (false)
      Permissions.isAllowed("api","read",authn) should === (true)
    }

    "not allow 'data' read and write for User Account" in {
      val uid = UUID("00000000-0000-0000-1000-000000000001")
      val authn = AuthenticatedUser(uid,roles = Seq("user"))
      Permissions.isAllowed("data","write",authn) should === (false)
      Permissions.isAllowed("data","read",authn) should === (false)
    }

    "allow Service User with `service` role" in {
      val uid = DefaultPermissions.USER_SERVICE
      val authn = AuthenticatedUser(uid,roles = Seq("service"))
      Permissions.isAllowedRole("service",authn) should === (true)      
    }

    "not allow 'data' write for User1, but allow for User2" in {
      val uid1 = UUID("00000000-0000-0000-1000-000000000001")
      val uid2 = UUID("00000000-0000-0000-1000-000000000002")
      val authn1 = AuthenticatedUser(uid1,roles = Seq("user"))
      val authn2 = AuthenticatedUser(uid2,roles = Seq("user","data"))

      Permissions.isAllowed("data","write",authn1) should === (false)
      Permissions.isAllowed("data","write",authn2) should === (true)
    }

  }
}
