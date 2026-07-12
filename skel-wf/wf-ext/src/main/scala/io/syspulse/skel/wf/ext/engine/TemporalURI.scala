package io.syspulse.skel.wf.ext.engine

import scala.util.Try

import io.syspulse.skel.util.Util

/*
temporal://host:port/namespace?options

All time options in milliseconds. Defaults:
  host: 127.0.0.1
  port: 7233
  namespace: default
  enable_keep_alive: true
  keep_alive_time: 30000
  keep_alive_timeout: 15000
  rpc_timeout: 10000
  auth: (none) - JWT token for Authorization: Bearer <token>
  tls: (none) - TLS mode:
    - (not set): plaintext gRPC connection (default)
    - "cert":   secure TLS with certificate validation
    - "ignore": disable certificate validation (DEV ONLY!)

The namespace may be:
  - a single name          (e.g. "default")
  - "*"                    (all namespaces on the server)
  - a comma-separated list (e.g. "ns1,ns2")

Examples:
  temporal://
  temporal://127.0.0.1:7233/default
  temporal://my-host:7233/my-namespace
  temporal://?namespace=prod&rpc_timeout=30000
  temporal://localhost:7233?auth=eyJhbGciOiJIUzI1NiIs...
  temporal://secure.temporal.io:7233?tls=cert
  temporal://dev-server:7233?tls=ignore

NOTE: this is a self-contained copy for the wf-ext Engine layer (mirrors
      io.syspulse.skel.wf.temporal.TemporalURI) so wf-ext stays decoupled from wf-temporal.
*/
case class TemporalURI(uri: String) {
  val PREFIX = "temporal://"

  val DEF_HOST = "127.0.0.1"
  val DEF_PORT = 7233
  val DEF_NAMESPACE = "default"
  val DEF_ENABLE_KEEP_ALIVE = true
  val DEF_KEEP_ALIVE_TIME = 30000L
  val DEF_KEEP_ALIVE_TIMEOUT = 15000L
  val DEF_RPC_TIMEOUT = 10000L

  private val (_host: String, _port: Int, _namespace: String, _ops: Map[String, String]) = parse(uri)

  def host: String = _host
  def port: Int = _port
  def namespace: String = _namespace
  def ops: Map[String, String] = _ops

  /** Target address for WorkflowServiceStubs (host:port). */
  def target: String = s"$host:$port"

  def enableKeepAlive: Boolean = _ops.get("enable_keep_alive").map(v => v.isEmpty || v.toBoolean).getOrElse(DEF_ENABLE_KEEP_ALIVE)
  def keepAliveTime: Long = _ops.get("keep_alive_time").map(_.toLong).getOrElse(DEF_KEEP_ALIVE_TIME)
  def keepAliveTimeout: Long = _ops.get("keep_alive_timeout").map(_.toLong).getOrElse(DEF_KEEP_ALIVE_TIMEOUT)
  def rpcTimeout: Long = _ops.get("rpc_timeout").map(_.toLong).getOrElse(DEF_RPC_TIMEOUT)

  /** Optional JWT auth token, used as Authorization: Bearer <token> on gRPC metadata. */
  def auth: Option[String] = _ops.get("auth").filter(_.nonEmpty)

  /** TLS mode: None (plaintext), Some("ignore") (insecure), Some("cert") (secure). */
  def tls: Option[String] = _ops.get("tls").filter(_.nonEmpty)

  def tlsInsecure: Boolean = tls.contains("ignore")
  def tlsSecure: Boolean = tls.contains("cert")

  /** Whether the namespace is a wildcard (all namespaces on the server). */
  def isAllNamespaces: Boolean = _namespace == "*"

  /** Namespaces explicitly listed in the URI ("ns1,ns2"); empty when wildcard/single. */
  def namespaceList: Seq[String] =
    if (_namespace.contains(",")) _namespace.split(",").map(_.trim).filter(_.nonEmpty).toSeq
    else Seq(_namespace)

  def parse(uri: String): (String, Int, String, Map[String, String]) = {
    val (url: String, ops: Map[String, String]) = uri.split("[\\?&]").toList match {
      case u :: Nil => (u, Map())
      case u :: rest =>
        val vars = rest.flatMap(_.split("=").toList match {
          case k :: Nil => Some(k -> "")
          case k :: v :: Nil => Some(k -> Util.replaceEnvVar(v))
          case _ => None
        }).toMap
        (u, vars)
      case _ => ("", Map())
    }

    val pathPart = url.stripPrefix(PREFIX).trim
    val (hostPort, namespaceFromPath) = pathPart.split("/", 2).toList match {
      case "" :: Nil | Nil => ("", None)
      case hp :: ns :: _ => (hp.trim, Some(ns.trim))
      case hp :: Nil => (hp.trim, None)
    }

    val (host, port) = hostPort.split(":").toList match {
      case h :: p :: _ => (h.trim, Try(p.trim.toInt).getOrElse(DEF_PORT))
      case h :: Nil => (if (h.trim.isEmpty) DEF_HOST else h.trim, DEF_PORT)
      case _ => (DEF_HOST, DEF_PORT)
    }

    val namespace = namespaceFromPath
      .orElse(ops.get("namespace"))
      .filter(_.nonEmpty)
      .getOrElse(DEF_NAMESPACE)

    (host, port, namespace, ops)
  }
}
