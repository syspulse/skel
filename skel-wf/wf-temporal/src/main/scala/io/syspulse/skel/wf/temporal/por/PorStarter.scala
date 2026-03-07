package io.syspulse.skel.wf.temporal.por

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.syspulse.skel.wf.temporal.ScalaDataConverter
import com.typesafe.scalalogging.Logger

object PorStarter {
  private val log = Logger(getClass.getName)

  def main(args: Array[String]): Unit = {
    // Parse command line arguments
    val config = parseArgs(args)

    // Get Temporal service address from environment or use default
    val temporalServiceAddress = sys.env.getOrElse("TEMPORAL_SERVICE_ADDRESS", "127.0.0.1:7233")

    log.info(s"Connecting to Temporal service at: $temporalServiceAddress")

    // Create service stub with proper timeouts
    val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
      .setTarget(temporalServiceAddress)
      .setEnableKeepAlive(true)
      .setKeepAliveTime(java.time.Duration.ofSeconds(30))
      .setKeepAliveTimeout(java.time.Duration.ofSeconds(15))
      .setRpcTimeout(java.time.Duration.ofSeconds(10))
      .build()

    val service = WorkflowServiceStubs.newServiceStubs(serviceOptions)

    // Create client with options (including Scala DataConverter)
    val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
      .setNamespace("default")
      .setDataConverter(ScalaDataConverter.create())
      .build()

    val client = WorkflowClient.newInstance(service, clientOptions)

    // Create workflow input
    val input = PorWorkflowInput(
      cexName = config.cexName,
      timestamp = System.currentTimeMillis(),
      pooRequired = config.pooRequired,
      porRequired = config.porRequired,
      polRequired = config.polRequired,
      reportRequired = config.reportRequired
    )

    // Create workflow options
    val workflowId = s"por-workflow-${config.cexName}-${System.currentTimeMillis()}"
    val options = WorkflowOptions.newBuilder()
      .setWorkflowId(workflowId)
      .setTaskQueue(PorWorker.TASK_QUEUE)
      .build()

    // Create workflow stub
    val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

    log.info(s"Starting PoR Workflow:")
    log.info(s"  Workflow ID: $workflowId")
    log.info(s"  CEX Name: ${config.cexName}")
    log.info(s"  Flow: ${config.flow}")
    log.info(s"  PoO Required: ${config.pooRequired}")
    log.info(s"  PoR Required: ${config.porRequired}")
    log.info(s"  PoL Required: ${config.polRequired}")
    log.info(s"  Report Required: ${config.reportRequired}")

    // Execute workflow
    val result = workflow.execute(input)

    log.info(s"Workflow completed successfully!")
    log.info(s"Report generated: ${result.reportFilePath}")
    log.info(s"Report link: ${result.reportLink}")

    // Cleanup
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
