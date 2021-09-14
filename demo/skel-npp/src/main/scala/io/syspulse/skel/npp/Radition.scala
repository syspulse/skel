package io.syspulse.skel.npp

import upickle._
import upickle.default._
import upickle.default.{ReadWriter => RW, macroRW}

case class Radiation(ts:Long,area:String,lat:Double,lon:Double,geohash:String,dose:Double)
object Radiation {
  implicit val rw: RW[Radiation] = macroRW
}