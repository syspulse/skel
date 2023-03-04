package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._

abstract class Status

object Status {
  case class INITIALIZED() extends Status
  case class CREATED() extends Status
  case class STARTED() extends Status
  case class STOPPED() extends Status
}
