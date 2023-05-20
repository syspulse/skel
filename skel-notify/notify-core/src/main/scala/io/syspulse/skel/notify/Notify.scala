package io.syspulse.skel.notify

import scala.collection.immutable

import io.jvm.uuid._

final case class Notify(
  to:Option[String] = None, 
  subj:Option[String] = None,
  msg:String = "", 
  ts:Long = System.currentTimeMillis(), 
  id:UUID = UUID.random,
  severity:Option[NotifySeverity.ID]=None,
  scope:Option[String]=None,
  uid:Option[UUID] = None,
  from:Option[UUID] = None,
  var ack:Boolean = false  
)
