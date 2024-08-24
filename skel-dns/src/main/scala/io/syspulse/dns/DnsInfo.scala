package io.syspulse.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger


case class DnsInfo(
  domain:String,
  created:Option[Long],
  updated:Option[Long],
  expire:Option[Long],
  ip:String,
  ns:Seq[String]
)
