package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

trait Running {
  def start():Try[Running]
  def stop():Try[Running]
  def !(e: ExecEvent):Try[Running]
}

abstract class Runtime {
  def spawn(link: Linking):Try[Running]
}
