package io.syspulse.skel.wf.ext.event

/** Inclusive lower bounds for Alert `se` (word) derived from Event `sev` / Alert `nse`. */
object Severity {
  val CRITICAL = 0.75
  val HIGH = 0.5
  val MEDIUM = 0.25
  val LOW = 0.15
  val INFO = 0.1
  val NONE = 0.0

  val LABEL_CRITICAL = "CRITICAL"
  val LABEL_HIGH = "HIGH"
  val LABEL_MEDIUM = "MEDIUM"
  val LABEL_LOW = "LOW"
  val LABEL_INFO = "INFO"

  /** NONE (below INFO) is stored as empty `se`. */
  def label(sev: Double): String =
    if (sev >= CRITICAL) LABEL_CRITICAL
    else if (sev >= HIGH) LABEL_HIGH
    else if (sev >= MEDIUM) LABEL_MEDIUM
    else if (sev >= LOW) LABEL_LOW
    else if (sev >= INFO) LABEL_INFO
    else ""
}
