package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import scala.concurrent.{ExecutionContext,Future}
import scala.collection.concurrent.TrieMap

import java.net.InetAddress
import java.time.OffsetDateTime
import io.syspulse.skel.HTTP

import spray.json._
import spray.json.DefaultJsonProtocol._
import io.syspulse.skel.service.JsonCommon

import org.xbill.DNS._

// --- RDAP Resolver -------------------------------------------------------------
// Reference response: https://rdap.identitydigital.services/rdap/domain/eth.limo

case class RdapEvent(eventAction: String, eventDate: String)

case class RdapNameserver(ldhName: Option[String] = None)

case class RdapDomainResponse(
  nameservers: Option[Seq[RdapNameserver]] = None,
  events: Option[Seq[RdapEvent]] = None
)

case class RdapBootstrap(services: Seq[Seq[Seq[String]]])

object RdapJson extends JsonCommon {
  implicit val jf_event: RootJsonFormat[RdapEvent] = jsonFormat2(RdapEvent)
  implicit val jf_nameserver: RootJsonFormat[RdapNameserver] = jsonFormat1(RdapNameserver)
  implicit val jf_domain: RootJsonFormat[RdapDomainResponse] = jsonFormat2(RdapDomainResponse)
  implicit val jf_bootstrap: RootJsonFormat[RdapBootstrap] = jsonFormat1(RdapBootstrap)
}

import RdapJson._

class RdapResolver(url0:Option[String] = None) extends DnsResolver {
  val log = Logger(s"${this}")
  
  private def parseEpochMilli(s:String):Long =
    OffsetDateTime.parse(s.trim).toInstant.toEpochMilli

  private def normalizeNsName(name:String):String =
    name.trim.split("\\s+").headOption.filter(_.nonEmpty).getOrElse(name.trim)

  def parseResponse(domain:String,body:String):Try[DnsInfo] = {
    Try {
      val rsp = body.parseJson.convertTo[RdapDomainResponse]

      val ns =
        rsp.nameservers
          .getOrElse(Seq.empty)
          .flatMap(_.ldhName)
          .map(normalizeNsName)
          .filter(_.nonEmpty)
          .toList

      val eventMap:Map[String,String] =
        rsp.events
          .getOrElse(Seq.empty)
          .map(e => e.eventAction.toLowerCase.trim -> e.eventDate.trim)
          .toMap

      val created = eventMap.get("registration").map(parseEpochMilli)
      val updated = eventMap.get("last changed").map(parseEpochMilli)
      val expire = eventMap.get("expiration").map(parseEpochMilli)

      DnsInfo(
        domain = domain,
        created = created,
        updated = updated,
        expire = expire,
        ip = "",
        ns = ns
      )
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
          log.debug(s"${domain}: rdap=${rdapDomainUrl}, body=${body}")

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

  private def extractTld(domain:String):Option[String] =
    domain.trim.split("\\.").lastOption.map(_.toLowerCase).filter(_.nonEmpty)

  /** Normalize base rdap URL into a domain endpoint URL prefix. */
  def normalizeDomainEndpoint(baseUrl:String):String = {
    val u = baseUrl.trim.stripSuffix("/")
    if(u.endsWith("/domain")) u else s"${u}/domain"
  }

  /** Parse IANA dns.json into a map of tld -> base rdap URL. */
  def parseBootstrap(json:String):Map[String,String] = {
    import RdapJson._
    json.parseJson.convertTo[RdapBootstrap].services.flatMap {
      case Seq(tlds, urls) =>
        urls.headOption.toList.flatMap { url =>
          tlds.map(_.trim.toLowerCase).filter(_.nonEmpty).map(_ -> url.trim)
        }
      case _ => Nil
    }.toMap
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
