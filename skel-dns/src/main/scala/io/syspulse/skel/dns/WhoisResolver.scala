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

// --- WHOIS -----------------------------------------------------------------------
class WhoisResolver extends DnsResolver {
  val log = Logger(s"${this}")

  def resolve(domain:String):Try[DnsInfo] = getInfo(domain,None)

  val tsFormatISO = Seq(
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")
  )

  def parseDate(date:String) = {
    tsFormatISO.map(f => Try(OffsetDateTime.parse(date,f)))
      .find(_.isSuccess)
      .map(_.get)
      .get
      .toInstant
      .toEpochMilli
  }

  // extracts root DNS zone and returns whois server
  def getZoneWhois(domain:String)(implicit whois:WhoisClient):Try[String] = {
    try {
      val zone = domain.split("\\.").lastOption
      if(!zone.isDefined)
        return Failure(new Exception(s"could not get zone: '${domain}'"))

      log.debug(s"${domain}: zone=${zone.get}")

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

  // this is not going to work for '.co.uk', so better not to use it
  def getDomain(userDomain:String):String = {
    // extract top level domain
    userDomain.split("\\.").toList match {
      case dom :: tld :: Nil => s"${dom}.${tld}"
      case dom :: Nil=> dom
      case dd => 
        // subdomain
        dd.takeRight(2).mkString(".")
    }    
  }

  def parseResponse(domain:String,r:String):Try[DnsInfo] = {
    
    if(r.startsWith("No match for")) {
      return Failure(new Exception(s"not found: ${domain}"))
    }

    // parse
    val ss = r.split("\n").map(_.trim).filter(! _.isBlank)

    val created = ss.filter(_.startsWith("Creation Date:")).flatMap(d => {        
      d.split("Creation Date:").toList match {
        case _ :: exp :: Nil =>
          val ts = parseDate(exp.trim) // DateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
          Some(ts)
        case v => 
          log.warn(s"failed to parse Creation Date: ${d}")
          None
      }
    }).headOption

    val updated = ss.filter(_.startsWith("Updated Date:")).flatMap(d => {
      d.split("Updated Date:").toList match {
        case _ :: exp :: Nil => 
          val ts = parseDate(exp.trim)
          Some(ts)
        case _ => 
          log.warn(s"failed to parse Update Date: ${d}")
          None
      }
    }).headOption

    val expire = ss.filter(_.startsWith("Registry Expiry Date:")).flatMap(d => {
      d.split("Registry Expiry Date:").toList match {
        case _ :: exp :: Nil => 
          val ts = parseDate(exp.trim) //OffsetDateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
          Some(ts)
        case _ => 
          log.warn(s"failed to parse Expiration Date: ${d}")
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
    
    Success(DnsInfo(
      domain = domain,
      created = created,
      updated = updated,
      expire = expire,
      ip = "",
      ns = ns.toIndexedSeq
    ))
  }

  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(userDomain:String,whoisServer0:Option[String] = None):Try[DnsInfo] = {
    if(userDomain.isBlank()) {
      return Failure(new Exception(s"invalid domain: '${userDomain}'"))
    }

    // this is not going to work for '.co.uk', so better 
    // allow to fail on subdomains
    val domain = userDomain //getDomain(userDomain)
    
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

      val dsn = parseResponse(domain,r)
      val addr:InetAddress = Address.getByName(domain)
      val ip = addr.getHostAddress()

      dsn match {
        case Success(dns) => 
          Success(dns.copy(ip = ip))
        case Failure(e) => 
          // treat dns failure as no dns info
          Success(DnsInfo(
            domain = domain,
            created = None,
            updated = None,
            expire = None,
            ip = ip,
            ns = Seq.empty,
            err = Some(e.getMessage)
          ))
      }

    } catch {
      case e:Exception => 

        Failure(e)
    }
  }
}
