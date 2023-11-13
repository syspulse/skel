package io.syspulse.skel.odometer

import scala.collection.immutable

import io.jvm.uuid._

final case class Odo(  
  id:String,
  counter:Long,
  ts:Long = System.currentTimeMillis()
)
