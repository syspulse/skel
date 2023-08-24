package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.Authenticated
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

import io.syspulse.skel.auth.permissions.Permissions

class PermissionsRbacEngineFile(store:String) extends PermissionsRbacEngine {
  
  def getUserRoles(uid:UUID):Seq[Role] = {
    Seq()
  }

  def getRolePermissions(role:Role):Seq[ResourcePermission] = {
    Seq()
  }
}

class PermissionsRbacFile(store:String) extends PermissionsRbac {  
  val engine = new PermissionsRbacEngineFile(store)
  
}
