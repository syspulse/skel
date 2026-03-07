package io.syspulse.skel.wf.temporal.por

import scala.util.Try

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger

object PorStarter {
  private val log = Logger(getClass.getName)

  def run(uri: String, args: Array[String]): Try[Unit] = Try {
    val config = parseArgs(args)

    val t = TemporalURI(uri)
    log.info(s"Connecting to Temporal at ${t.target} namespace=${t.namespace}")

    val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofSeconds(t.keepAliveTimeSec))
      .setKeepAliveTimeout(java.time.Duration.ofSeconds(t.keepAliveTimeoutSec))
      .setRpcTimeout(java.time.Duration.ofSeconds(t.rpcTimeoutSec))
      .build()

    val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

    val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
      .setNamespace(t.namespace)
      .setDataConverter(ScalaDataConverter.create())
      .build()

    val client = WorkflowClient.newInstance(service, clientOptions)

    val input = PorWorkflowInput(
      cexName = config.cexName,
      timestamp = System.currentTimeMillis(),
      pooRequired = config.pooRequired,
      porRequired = config.porRequired,
      polRequired = config.polRequired,
      reportRequired = config.reportRequired
    )

    val wid = s"por-workflow-${config.cexName}-${System.currentTimeMillis()}"
    val options = WorkflowOptions.newBuilder()
      .setWorkflowId(wid)
      .setTaskQueue(PorWorker.TASK_QUEUE)
      .build()

    val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

    log.info(s"Starting PoR Workflow: $wid: cexName=${config.cexName} flow=${config.flow} poo=${config.pooRequired} por=${config.porRequired} pol=${config.polRequired} report=${config.reportRequired}")

    val result = workflow.execute(input)

    log.info(s"$wid: Report=${result.reportFilePath}, link=${result.reportLink}")    

    service.shutdown()
  }

  private case class StarterConfig(
    cexName: String = "DefaultCEX",
    flow: String = "flow-1",
    pooRequired: Boolean = true,
    porRequired: Boolean = true,
    polRequired: Boolean = true,
    reportRequired: Boolean = true
  )

  private def parseArgs(args: Array[String]): StarterConfig = {
    if (args.isEmpty) {
      printUsage()
      return StarterConfig()
    }

    val flow = args(0).toLowerCase

    val (pooRequired, porRequired, polRequired, reportRequired) = flow match {
      case "flow-1" => (true, true, true, true)   // PoO -> PoR -> PoL -> Solvency -> Report
      case "flow-2" => (false, true, true, true)  // PoR -> PoL -> Solvency -> Report
      case "flow-3" => (false, true, false, true) // PoR -> Report
      case "flow-4" => (true, true, false, true)  // PoO -> PoR -> Report
      case _ =>
        log.warn(s"Unknown flow: $flow, using default flow-1")
        (true, true, true, true)
    }

    val cexName = if (args.length > 1) args(1) else "DefaultCEX"

    StarterConfig(
      cexName = cexName,
      flow = flow,
      pooRequired = pooRequired,
      porRequired = porRequired,
      polRequired = polRequired,
      reportRequired = reportRequired
    )
  }

  private def printUsage(): Unit = {
    log.info("""
Usage: PorStarter <flow> [cex-name]

Flows:
  flow-1  : PoO -> PoR -> PoL -> Solvency -> Report (default)
  flow-2  : PoR -> PoL -> Solvency -> Report
  flow-3  : PoR -> Report
  flow-4  : PoO -> PoR -> Report

Examples:
  PorStarter flow-1 Binance
  PorStarter flow-2 Coinbase
  PorStarter flow-3 Kraken
  PorStarter flow-4 Gemini
""")
  }
}
