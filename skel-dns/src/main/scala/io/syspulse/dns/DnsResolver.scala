package io.syspulse.dns

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


abstract class RegistryResolver {
  def resolve(domain:String):Try[DnsInfo]
}

// --- WHOIS -----------------------------------------------------------------------
class WhoisResolver extends RegistryResolver {
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

  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer0:Option[String] = None):Try[DnsInfo] = {
    if(domain.isBlank()) {
      return Failure(new Exception(s"invalid domain: '${domain}'"))
    }

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

// --- .to Resover -----------------------------------------------------------------------
class TonicResolver extends RegistryResolver {
  val log = Logger(s"${this}")
  
  val tsFormatTonic = DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss yyyy").withLocale(Locale.US)
  
// <pre>
// Domain:               across.to
// Created on:           Tue Oct 19 23:12:27 2021
// Last edited on:       Tue Sep 19 18:33:25 2023
// Expires on:           Sat Oct 19 23:12:27 2024
// Primary host add:     None
// Primary host name:    raegan.ns.cloudflare.com
// Secondary host add:   None
// Secondary host name:  west.ns.cloudflare.com

// END  

  def parseResponse(domain:String,body:String) = {
    // parse
      val ss = body.split("\n").map(_.trim).filter(! _.isBlank)

      val created = ss.filter(_.startsWith("Created on:")).flatMap(d => {        
        d.split("Created on:").toList match {
          case _ :: exp :: Nil =>
            val ts = LocalDateTime.parse(exp.trim,tsFormatTonic).toEpochSecond(ZoneOffset.UTC) * 1000L
            Some(ts)
          case v => 
            log.warn(s"failed to parse Creation Date: ${d}")
            None
        }
      }).headOption

      val updated = ss.filter(_.startsWith("Last edited on:")).flatMap(d => {
        d.split("Last edited on:").toList match {
          case _ :: exp :: Nil => 
            val ts = LocalDateTime.parse(exp.trim,tsFormatTonic).toEpochSecond(ZoneOffset.UTC) * 1000L
            Some(ts)
          case _ => 
            log.warn(s"failed to parse Update date: ${d}")
            None
        }
      }).headOption

      val expire = ss.filter(_.startsWith("Expires on:")).flatMap(d => {
        d.split("Expires on:").toList match {
          case _ :: exp :: Nil => 
            val ts = LocalDateTime.parse(exp.trim,tsFormatTonic).toEpochSecond(ZoneOffset.UTC) * 1000L
            Some(ts)
          case _ => 
            log.warn(s"failed to parse Expiration date: ${d}")
            None
        }
      }).headOption
      
      val ns = ss.filter(_.contains("host name:")).flatMap(ns => {
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
        ns = ns
      ))
  }

  def resolve(domain:String):Try[DnsInfo] = {
    if(domain.isBlank()) {
      return Failure(new Exception(s"invalid domain: '${domain}'"))
    }

    try {    
      val r = requests.get(s"https://www.tonic.to/whois?${domain}")      
      log.debug(s"${domain}: ${r}")

      r.statusCode match {
        case 200 =>
          parseResponse(domain,r.text())
            .map(di => {
              val addr:InetAddress = Address.getByName(domain)

              di.copy(
                ip = addr.getHostAddress()                
              )
            })

        case c => 
          //log.warn(s"failed to query: ${r}")
          Failure(new Exception(s"failed to query: ${r}"))
      }
      
    } catch {
      case e:Exception => 
        Failure(e)
    }
  }
}

