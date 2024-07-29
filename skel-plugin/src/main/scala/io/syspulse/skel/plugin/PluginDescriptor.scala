package io.syspulse.skel.plugin

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

object PluginDescriptor {
  type ID = String
}

case class PluginDescriptor(
  name:String,
  typ:String,     // type (class/jar,process,docker,script)

  init:String = "",    // instantiation class/process name/docker image  
  ver:String = "",    
  var data:Option[Map[String,Any]]=None
) 
