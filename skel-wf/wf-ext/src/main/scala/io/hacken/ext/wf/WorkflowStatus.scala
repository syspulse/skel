package io.hacken.ext.wf

/**
 * Canonical status vocabulary shared by `WorkflowConfig.status` and `DetectorConfig.status`.
 *
 * The BASELINE is the set of Temporal Engine execution statuses, folded into an engine-agnostic
 * vocabulary so callers never depend on a specific engine's enum names. Engine-specific states are
 * converted into these values by the Engine mapper - see
 * `io.syspulse.skel.wf.ext.engine.EngineStatus.fromTemporalWorkflow` / `fromTemporalActivity`.
 *
 * A few non-runtime lifecycle values (ACTIVE / DISABLED / DELETED) describe a config/detector that
 * exists but has not (yet) been resolved against a running Engine instance.
 */
object WorkflowStatus {
  // ---- Engine runtime execution statuses (Temporal baseline) ----
  val NEW              = "NEW"               // known but not yet started
  val SCHEDULED        = "SCHEDULED"         // scheduled, not yet started
  val RUNNING          = "RUNNING"
  val RUNNING_RETRY    = "RUNNING_RETRY"     // still RUNNING; a task/activity is retrying (not a failure); see meta.err
  val RUNNING_FAILED   = "RUNNING_FAILED"    // still RUNNING, but a task/activity has failed (not retrying) or no workers; see meta.err
  val WAITING          = "WAITING"           // running but blocked on a signal / human step
  val PAUSED           = "PAUSED"
  val COMPLETED        = "COMPLETED"
  val FAILED           = "FAILED"
  val TERMINATED       = "TERMINATED"
  val CANCELED         = "CANCELED"
  val TIMED_OUT        = "TIMED_OUT"
  val CONTINUED_AS_NEW = "CONTINUED_AS_NEW"
  val UNKNOWN          = "UNKNOWN"
  val UNRESOLVED       = "UNRESOLVED"        // runtime not present on the Engine (obsolete/removed id)
  val STARTING         = "STARTING"          // start initiated; run not yet visible on the Engine (visibility lag)

  // ---- lifecycle statuses (config/detector exists, no runtime bound yet) ----
  val ACTIVE      = "ACTIVE"
  val DISABLED    = "DISABLED"
  val DELETED     = "DELETED"
  val UNSPECIFIED = "UNSPECIFIED"

  /** The engine runtime statuses (the Temporal baseline). */
  val all: Set[String] = Set(
    NEW, SCHEDULED, RUNNING, RUNNING_RETRY, RUNNING_FAILED, WAITING, PAUSED, COMPLETED, FAILED,
    TERMINATED, CANCELED, TIMED_OUT, CONTINUED_AS_NEW, UNKNOWN, UNRESOLVED
  )

  /** Terminal (closed) runtime statuses. */
  def isTerminal(status: String): Boolean = status match {
    case COMPLETED | FAILED | TERMINATED | CANCELED | TIMED_OUT | CONTINUED_AS_NEW => true
    case _ => false
  }

  /** Statuses from which POST /config/{id}/start is allowed (must also have no xid). */
  val STARTABLE: Set[String] = Set(UNKNOWN, FAILED)

  def isStartable(status: String, xid: Option[String] = None): Boolean =
    !xid.exists(_.trim.nonEmpty) && STARTABLE.exists(_.equalsIgnoreCase(status))
}
