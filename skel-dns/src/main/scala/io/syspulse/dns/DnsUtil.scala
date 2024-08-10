package io.syspulse.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime

import org.apache.commons.net.whois.WhoisClient
import org.xbill.DNS._
import java.net.InetAddress

case class DnsInfo(
  domain:String,
  created:Option[Long],
  updated:Option[Long],
  expire:Option[Long],
  ip:String,
  ns:Seq[String]
)

object DnsUtil {
  val log = Logger(s"${this}")

  val tsFormatISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")

  // extracts root DNS zone and returns whois server
  def getZoneWhois(domain:String)(implicit whois:WhoisClient):Try[String] = {
    try {
      val zone = domain.split("\\.").lastOption
      if(!zone.isDefined)
        return Failure(new Exception(s"could not get zone: '${domain}'"))

      log.info(s"${domain}: ${zone.get}")

      whois.connect("whois.iana.org")
      val r = whois.query(s"${zone.get}")
      
      log.debug(s"${zone.get}: '${r}'")

      if(r.startsWith("No match for")) {
        return Failure(new Exception(s"not found: ${zone.get}"))
      }

      val ss = r.split("\n").map(_.trim).filter(! _.isBlank)
      val whoisZone = ss.filter(_.startsWith("whois:")).map(s => {
        s.split("whois:").toList match {
          case _ :: whois :: Nil =>
            Success(whois.trim)
          case v =>             
            Failure(new Exception(s"failed to parse whois server: ${s}"))
        }
      }).head

      whoisZone

    } catch {
      case e:Exception => Failure(e)
    }
  }

  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer0:Option[String] = None):Try[DnsInfo] = {
    try {
      // WhoisClient.DEFAULT_HOST
      val whois = new WhoisClient()
      
      val whoisServer = if(!whoisServer0.isDefined) {
        getZoneWhois(domain)(whois) match {
          case Success(ws) => ws
          case Failure(e) => return Failure(e)
        }
        
      } else
        whoisServer0.get
      
      whois.connect(whoisServer)
      
      val r = whois.query(s"${domain}")
      
      log.debug(s"${domain}: ${r}")

      if(r.startsWith("No match for")) {
        return Failure(new Exception(s"not found: ${domain}"))
      }

      // parse
      val ss = r.split("\n").map(_.trim).filter(! _.isBlank)

      val created = ss.filter(_.startsWith("Creation Date:")).flatMap(d => {        
        d.split("Creation Date:").toList match {
          case _ :: exp :: Nil =>
            val ts = OffsetDateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
            Some(ts)
          case v => 
            log.warn(s"failed to parse Creation Date: ${d}")
            None
        }
      }).headOption

      val updated = ss.filter(_.startsWith("Updated Date:")).flatMap(d => {
        d.split("Updated Date:").toList match {
          case _ :: exp :: Nil => 
            val ts = OffsetDateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
            Some(ts)
          case _ => 
            log.warn(s"failed to parse Expiration: ${d}")
            None
        }
      }).headOption

      val expire = ss.filter(_.startsWith("Registry Expiry Date:")).flatMap(d => {
        d.split("Registry Expiry Date:").toList match {
          case _ :: exp :: Nil => 
            val ts = OffsetDateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
            Some(ts)
          case _ => 
            log.warn(s"failed to parse Expiration: ${d}")
            None
        }
      }).headOption
      
      val ns = ss.filter(_.startsWith("Name Server:")).flatMap(ns => {
        ns.split(":").toList match {
          case _ :: server :: Nil => Some(server.trim)
          case _ => 
            log.warn(s"failed to parse NS: ${ns}")
            None
        }
      })

      val addr:InetAddress = Address.getByName(domain)

      Success(DnsInfo(
        domain = domain,
        created = created,
        updated = updated,
        expire = expire,
        ip = addr.getHostAddress(),
        ns = ns
      ))

    } catch {
      case e:Exception => 

        Failure(e)
    }
  }
}