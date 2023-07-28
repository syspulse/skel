package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import org.casbin.jcasbin.main.Enforcer
import io.syspulse.skel.auth.Authenticated
import org.casbin.jcasbin.model.Model
import org.casbin.jcasbin.persist.file_adapter.FileAdapter
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class PermissionsFile(modelFile:String,policyFile:String) extends Permissions() {
  
  log.info(s"model=${modelFile}, policy=${policyFile}")
  
  val enforcer = //new Enforcer(modelFile, policyFile);
    new Enforcer(
      {val m = new Model(); m.loadModelFromText(Util.loadFile(modelFile).toOption.getOrElse("")); m}, 
      new FileAdapter(new ByteArrayInputStream(Util.loadFile(policyFile).toOption.getOrElse("").getBytes(StandardCharsets.UTF_8)))
    )
  
  
}

