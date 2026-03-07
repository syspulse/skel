package io.syspulse.skel.wf.temporal.por

import scala.util.Try

import io.temporal.client.{WorkflowClient, WorkflowOptions}
import io.temporal.serviceclient.WorkflowServiceStubs
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger

case class PorConfig(
  cexName: String = "DefaultCEX",
  flow: String = "flow-1",
  pooRequired: Boolean = true,
  porRequired: Boolean = true,
  polRequired: Boolean = true,
  reportRequired: Boolean = true,
  /** PoL user signal: file | rest | simulate (default) */
  polSignalMode: String = "simulate"
)

object PorStarter {
  private val log = Logger(getClass.getName)

  def run(uri: String, config: PorConfig): Try[Unit] = Try {

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
      reportRequired = config.reportRequired,
      polSignalMode = config.polSignalMode
    )

    val wid = s"por-workflow-${config.cexName}-${System.currentTimeMillis()}"
    val options = WorkflowOptions.newBuilder()
      .setWorkflowId(wid)
      .setTaskQueue(PorWorker.TASK_QUEUE)
      .build()

    val workflow = client.newWorkflowStub(classOf[PorWorkflow], options)

    log.info(s"Starting PoR Workflow: $wid: cexName=${config.cexName} flow=${config.flow} polSignalMode=${config.polSignalMode} poo=${config.pooRequired} por=${config.porRequired} pol=${config.polRequired} report=${config.reportRequired}")

    val result = workflow.execute(input)

    log.info(s"$wid: Report=${result.reportFilePath}, link=${result.reportLink}")    

    service.shutdown()
  }
}
