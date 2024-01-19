package io.syspulse.skel.plugin

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.plugin.runtime._

object Plugin {
  type ID = String
}

case class Plugin(
  name:String,
  typ:String,     // type (class/jar,process,docker,script)

  init:String = "",    // instantiation class/process name/docker image  
  ver:String = "",    
  var data:Option[Map[String,Any]]=None
) 
