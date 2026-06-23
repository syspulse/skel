package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.{ExecutionContext,Future}

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime


class AutoResolver(server:Option[String] = None) extends DnsResolver {
  val log = Logger(s"${this}")

  private def mergeDns(d1: DnsInfo, d0: DnsInfo): DnsInfo =
    d1.copy(
      created = d1.created.orElse(d0.created),
      updated = d1.updated.orElse(d0.updated),
      expire = d1.expire.orElse(d0.expire),
      ip = if (d1.ip.isBlank) d0.ip else d1.ip,
      ns = if (d1.ns.isEmpty) d0.ns else d1.ns,
      err = d1.err.orElse(d0.err),
    )

  private def isResolved(d: DnsInfo): Boolean =
    d.created.isDefined && 
    d.updated.isDefined && 
    d.expire.isDefined &&
    !d.ip.isBlank && 
    d.ns.nonEmpty
  
  def resolve(domain:String)(implicit ec:ExecutionContext):Future[DnsInfo] = {
    val rr = domain.trim.split("\\.").last.toLowerCase match {
      case "to" => 
        // Tonic is deprecated, whois is not supported
        //new TonicResolver()
        Seq(new WhoisResolver())
      case "global" => Seq(new WhoisRootResolver())
      case "limo" => Seq(new RdapResolver(server))
      case "uk" => Seq(new UkResolver()) // co.uk actually
      case "test" => Seq(new TestResolver())
      // special case, such domain does not exit
      case "rdap" => Seq(new RdapResolver(server))
      case _ => Seq(new WhoisResolver(),new RdapResolver(server))
    }

    if (rr.isEmpty)
      Future.failed(new Exception(s"no resolver found for domain: '${domain}'"))
    else {
      val init = DnsInfo(domain = domain, created = None, updated = None, expire = None, ip = "", ns = Seq.empty)
      rr.foldLeft(Future.successful(init)) { (f, r) =>
        f.flatMap { merged =>
          if (isResolved(merged)) Future.successful(merged)
          else r.resolve(domain)(ec).map(d => mergeDns(merged, d))
        }
      }
    }
  }
}

object DnsUtil {
  val log = Logger(s"${this}")
  implicit val ec:ExecutionContext = scala.concurrent.ExecutionContext.global
  
  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer:Option[String] = None)(implicit ec:ExecutionContext = DnsUtil.ec):Future[DnsInfo] =
    getResolver(domain,whoisServer).resolve(domain)(ec)

  def getResolver(domain:String, server:Option[String] = None):DnsResolver = {
    domain.split("://").toList match {
      case "rdap" :: domain :: Nil =>
        new RdapResolver()
      case "whois" :: domain :: Nil =>
        new WhoisResolver()
      case _ =>        
        new AutoResolver()
    }
  }
}