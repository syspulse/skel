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

// --- .to Resover -----------------------------------------------------------------------
class TonicResolver extends DnsResolver {
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
