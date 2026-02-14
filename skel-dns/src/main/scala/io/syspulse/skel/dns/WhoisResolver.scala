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
// Second-level domains (2LD): multi-part public suffixes where the registrable domain
// is "label.2LD" (e.g. example.co.uk). See https://en.wikipedia.org/wiki/Second-level_domain
object WhoisResolver {
  val log = Logger(s"${this}")

  /** Known two-part (country-code) second-level domains. When present, registrable domain = last 3 parts. */
  private val secondLevelDomains: Set[String] = Set(
    // UK
    "co.uk", "ac.uk", "org.uk", "me.uk", "net.uk", "ltd.uk", "plc.uk", "gov.uk", "sch.uk", "mod.uk",
    // Japan
    "co.jp", "ac.jp", "ne.jp", "or.jp", "go.jp",
    // Australia
    "com.au", "net.au", "org.au", "edu.au", "gov.au", "asn.au", "id.au",
    // Austria, Bangladesh, Brazil, etc.
    "co.at", "or.at",
    "com.bd", "net.bd", "org.bd", "edu.bd", "ac.bd", "gov.bd",
    "com.br", "net.br", "org.br", "gov.br", "edu.br",
    // India
    "co.in", "com.in", "net.in", "org.in", "ac.in", "edu.in", "gov.in", "res.in",
    // New Zealand, Nigeria, Pakistan, South Africa, South Korea, etc.
    "co.nz", "net.nz", "org.nz", "ac.nz", "gov.nz",
    "com.ng", "org.ng", "gov.ng", "edu.ng", "net.ng", "sch.ng",
    "com.pk", "net.pk", "org.pk", "edu.pk", "gov.pk",
    "co.za", "org.za", "web.za", "net.za", "gov.za", "ac.za",
    "co.kr", "or.kr", "go.kr", "ac.kr", "ne.kr",
    // Sri Lanka, Thailand, Turkey, Ukraine, Spain, Russia, France
    "com.lk", "org.lk", "edu.lk", "gov.lk", "net.lk",
    "co.th", "ac.th", "go.th", "or.th", "in.th",
    "com.tr", "org.tr", "net.tr", "edu.tr", "gov.tr", "web.tr", "gen.tr",
    "com.ua", "org.ua", "net.ua", "edu.ua", "gov.ua", "in.ua", "co.ua",
    "com.es", "org.es", "nom.es", "gob.es", "edu.es",
    "ru", "com.ru", "org.ru", "net.ru", "edu.ru", "gov.ru",
    "com.fr", "asso.fr", "gouv.fr", "avocat.fr", "aeroport.fr",
    // Trinidad, Israel, Hungary, Netherlands
    "co.tt", "com.tt", "org.tt", "net.tt", "gov.tt", "edu.tt",
    "co.il", "org.il", "net.il", "gov.il", "ac.il",
    "co.hu", "org.hu", "gov.hu", "edu.hu",
    "co.nl", "nl"
  ).filter(_.contains(".")) // only multi-part (e.g. co.uk), exclude single TLDs like "ru"

  /** Extract registrable domain (for WHOIS): handles 2LDs like co.uk, ac.uk, co.jp. */
  def getDomain(userDomain: String): String = {
    val parts = userDomain.split("\\.").toList
    parts match {
      case dom :: Nil => dom
      case dom :: tld :: Nil => s"${dom}.${tld}"
      case dd if dd.size >= 3 =>
        val lastTwo = dd.takeRight(2).mkString(".")
        if (secondLevelDomains.contains(lastTwo))
          dd.takeRight(3).mkString(".")
        else
          dd.takeRight(2).mkString(".")
      case _ => userDomain
    }
  }

}

class WhoisResolver() extends DnsResolver {
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
      val root = "whois.iana.org"

      whois.connect(root)
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
          case List() =>
            log.debug(s"whois server missing, using root: '${root}'")
            Success(root)
          case v =>             
            Failure(new Exception(s"failed to parse whois server: '${s}': '${v}'"))
        }
      }).head

      whoisZone

    } catch {
      case e:Exception => Failure(e)
    }
  }

  def parseResponse(domain:String,r:String):Try[DnsInfo] = {
    
    if(r.startsWith("No match for")) {
      log.debug(s"registry not found: '${domain}'")
      return Failure(new Exception(s"not found: ${domain}"))
    }

    // parse
    val ss = r.split("\n").map(_.trim).filter(! _.isBlank)

    val created = ss.filter(_.startsWith(createdName)).flatMap(d => {        
      d.split(createdName).toList match {
        case _ :: exp :: Nil =>
          val ts = parseDate(exp.trim) // DateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
          Some(ts)
        case v => 
          log.warn(s"failed to parse Creation Date: ${d}")
          None
      }
    }).headOption

    val updated = ss.filter(_.startsWith(updatedName)).flatMap(d => {
      d.split(updatedName).toList match {
        case _ :: exp :: Nil => 
          val ts = parseDate(exp.trim)
          Some(ts)
        case _ => 
          log.warn(s"failed to parse Update Date: ${d}")
          None
      }
    }).headOption

    val expire = ss.filter(_.startsWith(expireName)).flatMap(d => {
      d.split(expireName).toList match {
        case _ :: exp :: Nil => 
          val ts = parseDate(exp.trim) //OffsetDateTime.parse(exp.trim,tsFormatISO).toInstant.toEpochMilli
          Some(ts)
        case _ => 
          log.warn(s"failed to parse Expiration Date: ${d}")
          None
      }
    }).headOption
    
    val ns = ss.filter(_.startsWith(nsName)).flatMap(ns => {
      ns.split(":").toList match {
        case _ :: server :: Nil => Some(server.trim)
        // can be extra info
        case _ :: server :: _ => Some(server.trim)
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
    val domain = WhoisResolver.getDomain(userDomain)
    
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
