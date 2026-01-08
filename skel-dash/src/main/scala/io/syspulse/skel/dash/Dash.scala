package io.syspulse.skel.dash

import scala.util.Try

object DashStatus {
  val ACTIVE = 0
  val DISABLED = -1
  val DELETED = -2
}

case class Dash (
  id:String,  // dashid
  
  layout:String, // this is json stringified layout
  
  name:Option[String] = None,
  info:Option[String] = None,  // was previously desc (SQL conflict)
  tags:Option[Vector[String]] = None,

  pid:Option[String], // project id
  tid:Option[String], // tenant id

  ts:Long = System.currentTimeMillis(), // update
  ts0:Long = System.currentTimeMillis(), // create

  status: Option[Int] = Some(DashStatus.ACTIVE),
)
