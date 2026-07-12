package io.syspulse.skel.wf.ext.engine

import scala.concurrent.{Future, ExecutionContext}

// ============================================================================
// Engine
//
// Abstraction over an Orchestration Runtime Engine (Temporal being the primary
// implementation). wf-ext only *observes* runtime state - orchestration and state
// ownership always live on the Engine side (see REQUIREMENTS-Workflow-Engine.md).
//
// Engine has two ID concepts:
//   - workflowId  : ID of the Workflow definition        (EngineWorkflow.id)
//   - runtimeId   : ID of the runtime Workflow instance  (EngineWorkflow.runtimeId,
//                   correlates to WorkflowConfig.xid - STRICT requirement)
// ============================================================================
trait Engine {

  /** Engine name as used in the API path, e.g. "temporal" (/api/v1/wf/ext/engine/temporal). */
  def name: String

  /**
   * Poll all runtime Workflows.
   *
   * @param namespace None -> all namespaces the engine exposes; Some(ns) -> a single namespace.
   * @param pageSize  max executions per namespace.
   *
   * Returns the top-level workflows (summary level - activities/children are NOT expanded here;
   * use [[getRuntime]] to expand a single instance).
   */
  def getRuntimes(namespace: Option[String] = None, pageSize: Int = 100): Future[Seq[EngineWorkflow]]

  /**
   * Get a single runtime Workflow by its `runtimeId` (Temporal RunId), fully expanded with
   * its Activities and Child-Workflows (child workflows matched by shared WorkflowId prefix
   * and resolved to their own RunIds).
   *
   * This pins a SPECIFIC run - the runtimeId never changes.
   *
   * @param namespace None -> search across all namespaces; Some(ns) -> only that namespace.
   */
  def getRuntime(namespace: Option[String], runtimeId: String): Future[Option[EngineWorkflow]]

  /**
   * Get the LATEST runtime Workflow for a `workflowId` (Temporal WorkflowId), fully expanded.
   *
   * A WorkflowId can have many runs over time (e.g. after a restart it gets a new RunId); this
   * resolves the most recent run, so the observed `runtimeId` may change across calls.
   *
   * @param namespace None -> search across all namespaces; Some(ns) -> only that namespace.
   */
  def getRuntimeByWorkflowId(namespace: Option[String], workflowId: String): Future[Option[EngineWorkflow]]

  /** List the namespaces exposed by the engine (excluding internal/system namespaces). */
  def namespaces(): Future[Seq[String]]

  /** Release engine resources (connections). */
  def close(): Unit
}

object Engine {

  val TEMPORAL = "temporal"

  /**
   * Build an Engine from a `--engine` URI.
   *   temporal://           -> Temporal engine (defaults 127.0.0.1:7233/default)
   *   temporal://host:port/ns?opts
   */
  def apply(uri: String)(implicit ec: ExecutionContext): Engine = {
    val u = Option(uri).map(_.trim).getOrElse("")
    scheme(u) match {
      case TEMPORAL => new TemporalEngine(u)
      case other    => throw new IllegalArgumentException(s"unsupported Engine: '${other}' (uri='${uri}'). Supported: temporal://")
    }
  }

  /** Extract the engine scheme from a `--engine` URI ("temporal://..." -> "temporal"). */
  def scheme(uri: String): String = {
    val u = Option(uri).map(_.trim).getOrElse("")
    val idx = u.indexOf("://")
    if (idx > 0) u.substring(0, idx).toLowerCase else u.toLowerCase
  }

  def isEngineUri(uri: String): Boolean = Option(uri).exists(_.contains("://"))
}
