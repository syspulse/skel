package io.syspulse.skel.wf.ext.event

import akka.actor.ActorSystem

import com.sksamuel.elastic4s.akka.{AkkaHttpClient, AkkaHttpClientSettings}
import com.sksamuel.elastic4s.ElasticClient

import io.syspulse.skel.uri.ElasticURI

object ElasticClients {

  /** Dedicated classic system for the elastic4s Akka HTTP client (Http pool / superPool). */
  private lazy val system: ActorSystem = ActorSystem("wf-ext-elastic4s")

  /**
   * Akka HTTP treats a missing port as 80/443 via the URI, but elastic4s hosts are `host:port`.
   * HTTPS without a port (AWS OpenSearch) must be `:443`; HTTP defaults to `:9200`.
   */
  def propertiesUrl(uri: ElasticURI): String = {
    val raw = uri.url.stripSuffix("/")
    val portlessHttps = raw.matches("""https://[^:/]+""")
    val portlessHttp = raw.matches("""http://[^:/]+""")
    if (portlessHttps) raw + ":443"
    else if (portlessHttp) raw + ":9200"
    else raw
  }

  def resolveIndex(uri: ElasticURI): String =
    Option(uri.index).map(_.trim).filter(i => i.nonEmpty && i != "index").getOrElse(EventStore.DEF_INDEX)

  def hostPort(uri: ElasticURI): (Boolean, String) = {
    val raw = propertiesUrl(uri)
    val https = raw.regionMatches(true, 0, "https://", 0, 8)
    val host = raw.replaceFirst("(?i)^https?://", "").stripSuffix("/")
    (https, host)
  }

  def connect(uri: ElasticURI): ElasticClient = {
    implicit val as: ActorSystem = system
    val (https, host) = hostPort(uri)
    val settings = AkkaHttpClientSettings(Seq(host)).copy(
      https = https,
      username = uri.user.map(_.trim).filter(_.nonEmpty),
      password = uri.pass.filter(_.nonEmpty),
      verifySSLCertificate = !uri.tlsInsecure,
    )
    ElasticClient(AkkaHttpClient(settings))
  }
}
