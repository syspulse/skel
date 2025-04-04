package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

object DnsUtil {
  val log = Logger(s"${this}")

  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer0:Option[String] = None):Try[DnsInfo] = {
    getResolver(domain).resolve(domain)
  }

  def getResolver(domain:String):DnsResolver = {
    domain.trim.split("\\.").last.toLowerCase match {
      case "to" => new TonicResolver()
      case "uk" => new UkResolver() // co.uk actually
      case "test" => new TestResolver()
      case _ => new WhoisResolver()
    }
  }
}