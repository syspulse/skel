package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext,Future}
import scala.collection.concurrent.TrieMap

import java.net.InetAddress
import org.xbill.DNS._
import java.time.OffsetDateTime
import _root_.io.syspulse.skel.HTTP

// --- RDAP Resolver -------------------------------------------------------------
// Reference response: https://rdap.identitydigital.services/rdap/domain/eth.limo
class RdapResolver(url0:Option[String] = None) extends DnsResolver {
  val log = Logger(s"${this}")
  
  private def parseEpochMilli(s:String):Long =
    OffsetDateTime.parse(s.trim).toInstant.toEpochMilli

  def parseResponse(domain:String,body:String):Try[DnsInfo] = {
    try {
      val nsSectionRe = """(?s)"nameservers"\s*:\s*\[(.*?)\]""".r
      val eventsSectionRe = """(?s)"events"\s*:\s*\[(.*?)\]""".r

      val ldhRe = """"ldhName"\s*:\s*"([^"]+)"""".r
      val eventRe = """(?s)\{[^}]*"eventAction"\s*:\s*"([^"]+)"[^}]*"eventDate"\s*:\s*"([^"]+)"[^}]*\}""".r

      val nsSection = nsSectionRe.findFirstMatchIn(body).map(_.group(1)).getOrElse("")
      val ns = ldhRe.findAllMatchIn(nsSection).map(_.group(1).trim).filter(_.nonEmpty).toList

      val eventsSection = eventsSectionRe.findFirstMatchIn(body).map(_.group(1)).getOrElse("")
      val eventMap:Map[String,String] =
        eventRe
          .findAllMatchIn(eventsSection)
          .map(m => m.group(1).toLowerCase.trim -> m.group(2).trim)
          .toMap

      val created = eventMap.get("registration").map(parseEpochMilli)
      val updated = eventMap.get("last changed").map(parseEpochMilli)
      val expire = eventMap.get("expiration").map(parseEpochMilli)

      Success(DnsInfo(
        domain = domain,
        created = created,
        updated = updated,
        expire = expire,
        ip = "",
        ns = ns
      ))
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def resolve(domain:String)(implicit ec:ExecutionContext):Future[DnsInfo] = {
    log.debug(s"resolving: '${domain}'")

    if(domain.isBlank()) {
      Future.failed(new Exception(s"invalid domain: '${domain}'"))
    } else {
      val baseUrlF:Future[String] =
        url0 match {
          case Some(u) => Future.successful(u)
          case None =>
            RdapResolver
              .getRdapBaseUrl(domain)
              .flatMap {
                case Some(u) => Future.successful(u)
                case None    => Future.failed(new Exception(s"RDAP bootstrap failed: ${domain}"))
              }
        }

      baseUrlF.flatMap { baseUrl =>
        val rdapDomainUrl = RdapResolver.normalizeDomainEndpoint(baseUrl)
        val url = s"${rdapDomainUrl}/${domain}"
        HTTP.get(url).map { body =>
          log.debug(s"${domain}: rdap=${rdapDomainUrl}")

          val di = parseResponse(domain, body)
          val addr:InetAddress = Address.getByName(domain)
          val ip = addr.getHostAddress()

          di match {
            case Success(dns) =>
              dns.copy(ip = ip)
            case Failure(e) =>
              DnsInfo(
                domain = domain,
                created = None,
                updated = None,
                expire = None,
                ip = ip,
                ns = Seq.empty,
                err = Seq(e.getMessage)
              )
          }
        }(ec).recoverWith { case e:Throwable =>
          Future.failed(new Exception(s"RDAP resolve failed: domain=${domain}: url=${url}: ${e.getMessage}", e))
        }(ec)
      }(ec)
    }
  }
}

object RdapResolver {
  val log = Logger(s"${this}")

  val IANA_DNS_BOOTSTRAP_URL = "https://data.iana.org/rdap/dns.json"

  // tld -> rdap base url (e.g. https://rdap.identitydigital.services/rdap/)
  private val tldUrlCache = TrieMap.empty[String,String]

  // Cache parsed bootstrap file (tld -> base url)
  @volatile private var bootstrapF:Option[Future[Map[String,String]]] = None

  private val servicesPairRe = """(?s)\[\s*\[(.*?)\]\s*,\s*\[(.*?)\]\s*\]""".r
  private val quotedStrRe = """"([^"]+)"""".r

  private def extractTld(domain:String):Option[String] =
    domain.trim.split("\\.").lastOption.map(_.toLowerCase).filter(_.nonEmpty)

  /** Normalize base rdap URL into a domain endpoint URL prefix. */
  def normalizeDomainEndpoint(baseUrl:String):String = {
    val u = baseUrl.trim.stripSuffix("/")
    if(u.endsWith("/domain")) u else s"${u}/domain"
  }

  /** Parse IANA dns.json into a map of tld -> base rdap URL. */
  def parseBootstrap(json:String):Map[String,String] = {
    servicesPairRe
      .findAllMatchIn(json)
      .flatMap(m => {
        val tldsRaw = m.group(1)
        val urlsRaw = m.group(2)

        val tlds = quotedStrRe.findAllMatchIn(tldsRaw).map(_.group(1).trim.toLowerCase).filter(_.nonEmpty).toList
        val urls = quotedStrRe.findAllMatchIn(urlsRaw).map(_.group(1).trim).filter(_.nonEmpty).toList

        urls.headOption.toList.flatMap(u => tlds.map(_ -> u))
      })
      .toMap
  }

  private def loadBootstrap()(implicit ec:ExecutionContext):Future[Map[String,String]] =
    bootstrapF match {
      case Some(f) => f
      case None =>
        this.synchronized {
          bootstrapF match {
            case Some(f) => f
            case None =>
              val f = HTTP
                .get(IANA_DNS_BOOTSTRAP_URL)
                .map(parseBootstrap)(ec)
                .recover { case _ => Map.empty[String,String] }(ec)
              bootstrapF = Some(f)
              f
          }
        }
    }

  /** Bootstraps and caches base URL for given domain's TLD. */
  def getRdapBaseUrl(domain:String)(implicit ec:ExecutionContext):Future[Option[String]] =
    extractTld(domain) match {
      case None => Future.successful(None)
      case Some(t) =>
        tldUrlCache.get(t) match {
          case Some(u) => Future.successful(Some(u))
          case None =>
            loadBootstrap().map { boot =>
              boot.get(t).map { u =>
                tldUrlCache.putIfAbsent(t,u)
                u
              }
            }(ec)
        }
    }
}

