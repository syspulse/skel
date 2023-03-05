package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

abstract class Running {
  def start():Unit
  def stop():Unit
  def !(e: ExecEvent):Unit
}

abstract class Runtime {
  def spawn(link: Linking):Running
}
