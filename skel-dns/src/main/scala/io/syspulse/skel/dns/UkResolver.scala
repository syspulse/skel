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

// --- co.uk -----------------------------------------------------------------------
                                                                   
// Domain name:           
//     example.co.uk
                                                                
// Registrant:
//     Nominet UK

// Registrant type:
//     UK Limited Company, (Company number: 3203859)

// Registrant's address:
//     Minerva House
//     Edmund Halley Road
//     Oxford Science Park
//     Oxford
//     Oxon
//     OX4 4DQ
//     United Kingdom

// Data validation:
//     Nominet was able to match the registrant's name and address against a 3rd party data source on 26-Oct-2018

// Registrar:
//     No registrar listed.  This domain is registered directly with Nominet.

// Relevant dates:
//     Registered on: 26-Nov-1996
//     Last updated:  10-Nov-2022

// Registration status:
//     No registration status listed.

// Name servers:
//     curt.ns.cloudflare.com
//     dee.ns.cloudflare.com

// DNSSEC:
//     Signed

// WHOIS lookup made at 12:34:45 23-Nov-2024

class UkResolver extends WhoisResolver {
    
  val tsFormatUk = Seq(
    DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)
  )

  def parseUkDate(date:String) = {
    tsFormatUk.map(f => Try(LocalDate.parse(date.trim,f)))
      .find(_.isSuccess)
      .map(_.get)
      .get
      .atStartOfDay()
      .toInstant(ZoneOffset.UTC)
      .toEpochMilli
  }

  override def parseResponse(domain:String,body:String):Try[DnsInfo] = {
    // parse
      var created:Option[Long] = None
      var updated:Option[Long] = None
      var expire:Option[Long] = None
      var ns:Seq[String] = Seq.empty
      var nameservers = false

      val ss = body.split("\n").map(line => {
        
        if(line.trim.startsWith("Registered on:")) {
          created = line.split("Registered on:").toList match {
            case _ :: exp :: Nil =>
              val ts = parseUkDate(exp)
              Some(ts)
            case v => 
              log.warn(s"failed to parse Register Date: ${line}")
              None
          }
        }

        if(line.trim.startsWith("Last updated:")) {
          updated = line.split("Last updated:").toList match {
            case _ :: exp :: Nil => 
              val ts = parseUkDate(exp)
              Some(ts)
            case _ => 
              log.warn(s"failed to parse Update date: ${line}")
              None
          }
        }

        // there is no expiration date in the response !!!
        // if(line.trim.startsWith("Expires on:")) {
        //   expire = line.split("Expires on:").toList match {
        //     case _ :: exp :: Nil => 
        //       val ts = parseUkDate(exp)
        //       Some(ts)
        //     case _ => 
        //       log.warn(s"failed to parse Expiration date: ${line}")
        //       None
        //   }
        // }        
        
        if(nameservers && line.isBlank) {
          nameservers = false
        }

        if(nameservers) {
          // nameserver can be in the format:
          //     curt.ns.cloudflare.com
          //     dee.ns.cloudflare.com
          // or  with ip address:
          // ns1.domainlore.uk         46.101.18.138
          // ns2.domainlore.uk         46.101.18.138

          ns = ns :+ line.trim.split("\\s+").head
        }

        if(line.contains("Name servers:")) {
          // next lines until blank line are nameservers
          nameservers = true
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
}
