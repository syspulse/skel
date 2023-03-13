package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._

object Exec {
  type ID = String

  def apply(name:String):ID = id(name)
  def id(name:String):ID = name
  def id(wid:Workflow.ID,name:String):ID = s"${wid}-${name}"
}

object Link {
  type ID = String
}

abstract class Let(id:Let.ID)
case class In(id:Let.ID) extends Let(id)
case class Out(id:Let.ID) extends Let(id)

object Let {
  type ID = String  
}

case class Link(id:Link.ID,from:Exec.ID,out:Let.ID,to:Exec.ID,in:Let.ID)

case class Exec(name:String,typ:String,in:Seq[In] = Seq(),out:Seq[Out] = Seq()) {
  private val id = Exec(name)
    
  def getId = Exec(name)
  def getName = name

}
