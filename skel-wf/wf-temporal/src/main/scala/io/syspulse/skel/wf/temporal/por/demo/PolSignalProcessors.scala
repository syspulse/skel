package io.syspulse.skel.wf.temporal.por.demo

import com.typesafe.scalalogging.Logger
import spray.json._

import io.syspulse.skel.wf.temporal.por._

/** Context passed to PoL signal processors. */
case class PolSignalContext(
  workflowId: String,
  runId: String,
  wid: String,
  log: Logger,
  signalPollIntervalMs: Long = 5000L
)

/** Processor for a specific PoL signal mode: waits for signal and resolves to PolFileData. */
trait PolSignalProcessor {
  def waitAndResolve(ctx: PolSignalContext, fallback: PolFileData): PolFileData
}

/** File signal: poll /tmp/por-pol-{workflowId}.signal; always returns fallback. */
object PolFileSignalProcessor extends PolSignalProcessor {
  override def waitAndResolve(ctx: PolSignalContext, fallback: PolFileData): PolFileData = {
    val safeWid = ctx.workflowId.replaceAll("[^a-zA-Z0-9_.-]", "_")
    val path = os.Path(s"/tmp/por-pol-${safeWid}.signal", os.pwd)
    ctx.log.info(s"${ctx.wid} Waiting for signal file: $path (poll every ${ctx.signalPollIntervalMs}ms)")
    var attempt = 0
    var done = false
    while (!done) {
      attempt += 1
      val content = scala.util.Try(os.read(path)).toOption.flatMap(s => Some(s.trim)).find(_.nonEmpty)
      if (content.isDefined) {
        ctx.log.info(s"${ctx.wid} Signal file received (attempt $attempt)")
        done = true
      } else {
        ctx.log.info(s"${ctx.wid} Poll attempt $attempt: signal file missing or empty, retrying in ${ctx.signalPollIntervalMs}ms")
        Thread.sleep(ctx.signalPollIntervalMs)
      }
    }
    fallback
  }
}

/** API signal: uses Temporal SDK to query workflow for signal data (survives worker restarts). */
object PolApiSignalProcessor extends PolSignalProcessor {
  import io.temporal.activity.Activity
  import io.temporal.client.WorkflowClient
  import io.temporal.serviceclient.WorkflowServiceStubs
  import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}

  override def waitAndResolve(ctx: PolSignalContext, fallback: PolFileData): PolFileData = {
    ctx.log.info(s"${ctx.wid} API signal: POST to /api/v1/wf/run/${ctx.runId}/signal with data to continue")

    // Get Temporal client to query workflow
    val activityInfo = Activity.getExecutionContext.getInfo

    // Connect to Temporal server (use environment variable or default)
    val temporalUri = sys.env.getOrElse("TEMPORAL_URI", "temporal://localhost:7233")
    val t = TemporalURI(temporalUri)

    val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .build()

    val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

    val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
      .setNamespace(t.namespace)
      .setDataConverter(ScalaDataConverter.create())
      .build()

    val client = WorkflowClient.newInstance(service, clientOptions)

    try {
      // Get workflow stub to query
      val workflowStub = client.newWorkflowStub(classOf[PorWorkflow], activityInfo.getWorkflowId, java.util.Optional.of(activityInfo.getRunId))

      // Poll for signal data using Temporal query (survives worker restarts)
      val maxAttempts = (24 * 3600 * 1000L / ctx.signalPollIntervalMs).toInt // 24 hours
      var attempt = 0
      var signalData: Option[PolFileData] = None

      while (attempt < maxAttempts && signalData.isEmpty) {
        attempt += 1

        try {
          // Query workflow for signal data
          val data = workflowStub.getPolSignalData()

          if (data.isDefined) {
            ctx.log.info(s"${ctx.wid} Signal data received from workflow (attempt $attempt)")

            // Parse signal data to PolFileData
            try {
              import spray.json.DefaultJsonProtocol._
              import PolJsonProtocol._
              signalData = Some(data.get.convertTo[PolFileData])
              ctx.log.info(s"${ctx.wid} Using API signal data with ${signalData.get.liabilities.size} liabilities")
            } catch {
              case e: Exception =>
                ctx.log.error(s"${ctx.wid} Failed to parse signal data: ${e.getMessage}, using fallback")
                signalData = Some(fallback)
            }
          } else {
            if (attempt % 12 == 0) { // Log every minute (if polling every 5s)
              ctx.log.info(s"${ctx.wid} Waiting for signal (attempt $attempt/$maxAttempts)")
            }
            Thread.sleep(ctx.signalPollIntervalMs)
          }
        } catch {
          case e: Exception =>
            ctx.log.warn(s"${ctx.wid} Error querying workflow (attempt $attempt): ${e.getMessage}")
            Thread.sleep(ctx.signalPollIntervalMs)
        }
      }

      if (signalData.isEmpty) {
        ctx.log.warn(s"${ctx.wid} Signal timeout after $attempt attempts, using fallback")
        fallback
      } else {
        signalData.get
      }
    } finally {
      service.shutdown()
    }
  }
}

/** Simulate signal: delay only; always returns fallback. */
object PolSimulateSignalProcessor extends PolSignalProcessor {
  override def waitAndResolve(ctx: PolSignalContext, fallback: PolFileData): PolFileData = {
    PorActivitiesDemo.simulateWork(2, 4)
    fallback
  }
}

object PolSignalProcessors {
  def get(mode: String): PolSignalProcessor = mode.toLowerCase match {
    case "file"   => PolFileSignalProcessor
    case "api"    => PolApiSignalProcessor
    case "simulate" | _ => PolSimulateSignalProcessor
  }
}
