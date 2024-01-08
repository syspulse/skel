package io.syspulse.skel.auth.permissions

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

object DefaultPermissions {
  val log = Logger(s"${this}")

  val USER_NOBODY = UUID("00000000-0000-0000-0000-000000000000")
  val USER_ADMIN =  UUID("ffffffff-0000-0000-9000-000000000001")
  val USER_SERVICE =UUID("eeeeeeee-0000-0000-1000-000000000001")

  val USER_1 = UUID("00000000-0000-0000-1000-000000000001")
  val USER_2 = UUID("00000000-0000-0000-1000-000000000002")

  val ROLE_ADMIN = "admin"      
  val ROLE_SERVICE = "service"  // service (skel-enroll -> skel-notify)
  val ROLE_USER = "user"        // user (external -> API)
  val ROLE_NOBODY = "nobody"      // non-existing user (new user or anonymous -> API)

}
