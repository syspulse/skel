package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.{ExecutionContext,Future}

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

object DnsUtil {
  val log = Logger(s"${this}")
  implicit val ec:ExecutionContext = scala.concurrent.ExecutionContext.global

  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer0:Option[String] = None)(implicit ec:ExecutionContext = DnsUtil.ec):Future[DnsInfo] =
    getResolver(domain).resolve(domain)(ec)

  def getResolver(domain:String, server:Option[String] = None):DnsResolver = {
    domain.trim.split("\\.").last.toLowerCase match {
      case "to" => 
        // Tonic is deprecated, whois is not supported
        //new TonicResolver()
        new WhoisResolver()
      case "global" => new WhoisRootResolver()
      case "limo" => new RdapResolver(server)
      case "uk" => new UkResolver() // co.uk actually
      case "test" => new TestResolver()
      // special case, such domain does not exit
      case "rdap" => new RdapResolver(server)
      case _ => new WhoisResolver()
    }
  }
}