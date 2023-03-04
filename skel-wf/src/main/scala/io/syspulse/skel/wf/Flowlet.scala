package io.syspulse.skel.wf

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._

object Flowlet {
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

case class Link(id:Link.ID,from:Flowlet.ID,out:Let.ID,to:Flowlet.ID,in:Let.ID)

case class Flowlet(name:String,typ:String,in:Seq[In] = Seq(),out:Seq[Out] = Seq()) {
  val id = Flowlet(name)
  val log = Logger(s"${this}-${id}")
  
  def getId = id
  def getName = name

  // def spawn(wid:Workflowing.ID):Try[Status] = {    
  //   val f = new Flowing(wid,name)
  //   log.info(s"wid=${wid}: ${f}")
  //   Success(f.status)
  // }

}
