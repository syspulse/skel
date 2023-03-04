package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._

case class FlowingData(attr:Map[String,Any]) {

}

object FlowingData {
  def empty:FlowingData = FlowingData(Map())
}

