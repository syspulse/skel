package io.syspulse.skel.dns

import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import scala.concurrent.{ExecutionContext,Future}

import java.time.format.DateTimeFormatter
import java.time.OffsetDateTime
import java.util.concurrent.TimeoutException

import io.syspulse.skel.FutureUtil


class AutoResolver(server:Option[String] = None, timeout: Long = DnsUtil.TIMEOUT) extends DnsResolver {
  val log = Logger(s"${this}")

  private def mergeDns(d1: DnsInfo, d0: DnsInfo): DnsInfo =
    d1.copy(
      created = d1.created.orElse(d0.created),
      updated = d1.updated.orElse(d0.updated),
      expire = d1.expire.orElse(d0.expire),
      ip = if (d1.ip.isBlank) d0.ip else d1.ip,
      ns = if (d1.ns.isEmpty) d0.ns else d1.ns,
      err = d1.err,
    )

  private def isResolved(d: DnsInfo): Boolean =
    d.created.isDefined && 
    d.updated.isDefined && 
    d.expire.isDefined &&
    !d.ip.isBlank && 
    d.ns.nonEmpty

  private def resolveWithTimeout(r: DnsResolver, domain: String)(implicit ec: ExecutionContext): Future[DnsInfo] =
    FutureUtil.withTimeout(r.resolve(domain)(ec), timeout)

  private def onResolveFailure(domain: String, d1: DnsInfo, e: Throwable): DnsInfo = {
    val msg = e match {
      case _: TimeoutException => s"timeout: ${timeout} ms"
      case _ => Option(e.getMessage).getOrElse(e.getClass.getSimpleName)
    }
    log.warn(s"failed to resolve: '${domain}': ${msg}")
    d1.copy(err = d1.err :+ msg)
  }
  
  def resolve(domain:String)(implicit ec:ExecutionContext):Future[DnsInfo] = {
    val rr = domain.trim.split("\\.").last.toLowerCase match {
      case "to" => 
        // Tonic is deprecated, whois is not supported
        //new TonicResolver()
        Seq(new WhoisResolver())
      case "global" => Seq(new WhoisRootResolver())
      case "limo" => Seq(new RdapResolver(server))
      case "uk" => Seq(new UkResolver()) // co.uk actually
      case "fi" => Seq(new FiResolver())
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
        f.flatMap { d1 =>
          if (isResolved(d1)) 
            Future.successful(d1)
          else 
            resolveWithTimeout(r, domain)
              .map(d0 => mergeDns(d1, d0))
              .recover { case e: Throwable => onResolveFailure(domain, d1, e) }
        }
      }.flatMap { d =>
        if (d.err.size == rr.size)
          Future.failed(new Exception(d.err.mkString("; ")))
        else
          Future.successful(d)
      }
    }
  }
}

object DnsUtil {
  val log = Logger(s"${this}")
  implicit val ec:ExecutionContext = scala.concurrent.ExecutionContext.global

  val TIMEOUT = 60000L  
  
  // default: "whois.internic.net"
  // whois.iana.org
  // To get WHOIS for .io zone:
  // 
  def getInfo(domain:String,whoisServer:Option[String] = None)(implicit ec:ExecutionContext = DnsUtil.ec):Future[DnsInfo] = {
    val (r,d) = getResolver(domain,whoisServer)
    r.resolve(d)(ec)
  }

  def getResolver(domain:String, server:Option[String] = None):(DnsResolver,String) = {
    domain.trim.toLowerCase.split("://").toList match {
      case "rdap" :: domain :: Nil =>
        (new RdapResolver(),domain)
      case "whois" :: domain :: Nil =>
        (new WhoisResolver(),domain)
      case ("http" | "https") :: domain :: Nil =>
        (new AutoResolver(),domain)
      case _ =>        
        (new AutoResolver(),domain)
    }
  }
}