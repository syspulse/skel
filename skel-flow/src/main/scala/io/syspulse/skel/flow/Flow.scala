package io.syspulse.skel.flow

import com.typesafe.scalalogging.Logger

case class Flow[F](id:FlowID,data:F,pipeline:Pipeline[F],var location:String)

case class FlowID(id:String)
object FlowID {

  def apply(name:String,ts:Long):FlowID = new FlowID(s"${if(name.isBlank) "" else s"${name}-"}${ts.toString}")
  def apply(name:String=""):FlowID = apply(name,System.currentTimeMillis)
}


