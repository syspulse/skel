package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

import org.apache.commons.net.whois.WhoisClient
import org.xbill.DNS._
import java.net.InetAddress
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import java.time.LocalDate
import scala.util.Random

class TestResolver extends WhoisResolver {

  override def resolve(domain:String):Try[DnsInfo] = {
    Success(DnsInfo(
      domain = domain,
      created = Some(0L),
      updated = Some(0L),
      expire = Some(0L + 3600L),
      ip = s"10.0.0.${Random.nextInt(2)+1}",
      ns = Seq("ns1.server.test","ns2.server.test")
    ))
  }
}
