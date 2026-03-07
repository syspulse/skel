package io.syspulse.skel.wf.temporal

import scala.util.Try

import io.syspulse.skel.util.Util

/*
temporal://host:port/namespace?options

Defaults (aligned with PorStarter/PorWorker):
  host: 127.0.0.1
  port: 7233
  namespace: default
  enable_keep_alive: true
  keep_alive_time_sec: 30
  keep_alive_timeout_sec: 15
  rpc_timeout_sec: 10

Examples:
  temporal://
  temporal://127.0.0.1:7233/default
  temporal://my-host:7233/my-namespace
  temporal://?namespace=prod&rpc_timeout_sec=30
*/
case class TemporalURI(uri: String) {
  val PREFIX = "temporal://"

  val DEF_HOST = "127.0.0.1"
  val DEF_PORT = 7233
  val DEF_NAMESPACE = "default"
  val DEF_ENABLE_KEEP_ALIVE = true
  val DEF_KEEP_ALIVE_TIME_SEC = 30L
  val DEF_KEEP_ALIVE_TIMEOUT_SEC = 15L
  val DEF_RPC_TIMEOUT_SEC = 10L

  private val (_host: String, _port: Int, _namespace: String, _ops: Map[String, String]) = parse(uri)

  def host: String = _host
  def port: Int = _port
  def namespace: String = _namespace
  def ops: Map[String, String] = _ops

  /** Target address for WorkflowServiceStubs (host:port). */
  def target: String = s"$host:$port"

  def enableKeepAlive: Boolean = _ops.get("enable_keep_alive").map(v => v.isEmpty || v.toBoolean).getOrElse(DEF_ENABLE_KEEP_ALIVE)
  def keepAliveTimeSec: Long = _ops.get("keep_alive_time_sec").map(_.toLong).getOrElse(DEF_KEEP_ALIVE_TIME_SEC)
  def keepAliveTimeoutSec: Long = _ops.get("keep_alive_timeout_sec").map(_.toLong).getOrElse(DEF_KEEP_ALIVE_TIMEOUT_SEC)
  def rpcTimeoutSec: Long = _ops.get("rpc_timeout_sec").map(_.toLong).getOrElse(DEF_RPC_TIMEOUT_SEC)

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
      case "" :: Nil | Nil => ( "", None )
      case hp :: ns :: _ => ( hp.trim, Some(ns.trim) )
      case hp :: Nil => ( hp.trim, None )
    }

    val (host, port) = hostPort.split(":").toList match {
      case h :: p :: _ => ( h.trim, Try(p.trim.toInt).getOrElse(DEF_PORT) )
      case h :: Nil => ( if (h.trim.isEmpty) DEF_HOST else h.trim, DEF_PORT )
      case _ => ( DEF_HOST, DEF_PORT )
    }

    val namespace = namespaceFromPath
      .orElse(ops.get("namespace"))
      .filter(_.nonEmpty)
      .getOrElse(DEF_NAMESPACE)

    (host, port, namespace, ops)
  }
}
