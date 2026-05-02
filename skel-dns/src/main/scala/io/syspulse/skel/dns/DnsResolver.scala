package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{Await,ExecutionContext,Future}
import scala.concurrent.duration._

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

import org.apache.commons.net.whois.WhoisClient
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

import io.syspulse.skel.FutureUtil

abstract class DnsResolver {
  def nsName:String = "Name Server:"
  def createdName:String = "Creation Date:"
  def updatedName:String = "Updated Date:"
  def expireName:String = "Registry Expiry Date:"

  def resolve(domain:String)(implicit ec:ExecutionContext):Future[DnsInfo]
}
