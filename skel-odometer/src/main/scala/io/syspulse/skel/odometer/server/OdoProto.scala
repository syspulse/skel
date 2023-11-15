package io.syspulse.skel.odometer.server

import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.odometer.Odo

final case class Odos(odometers: immutable.Seq[Odo])

final case class OdoCreateReq(id:String, counter:Option[Long] = Some(0L))
final case class OdoUpdateReq(id:String, delta:Long)

final case class OdoRes(odometer: Option[Odo])
