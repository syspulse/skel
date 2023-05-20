package io.syspulse.skel.job

import scala.collection.immutable
import io.jvm.uuid._

final case class JobNotification(
  id:UUID,  // job id
  inputs:Map[String,Any],
  uid:Option[UUID],
  state:String,
  result:Option[String],
  src:String,

  typ:String = "job",  
  ts:Long = System.currentTimeMillis,  
)

