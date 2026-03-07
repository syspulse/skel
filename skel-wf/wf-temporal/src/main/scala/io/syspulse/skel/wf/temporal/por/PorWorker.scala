package io.syspulse.skel.wf.temporal.por

import scala.util.Try

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.{Worker, WorkerFactory}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger

object PorWorker {
  private val log = Logger(getClass.getName)

  val TASK_QUEUE = "por-task-queue"

  def run(uri: String, impl: PorActivities): Try[Unit] = Try {
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

    val workerFactoryOptions = io.temporal.worker.WorkerFactoryOptions.newBuilder()
      .build()

    val factory = WorkerFactory.newInstance(client, workerFactoryOptions)

    val workerOptions = io.temporal.worker.WorkerOptions.newBuilder()
      .build()

    val worker = factory.newWorker(TASK_QUEUE, workerOptions)

    worker.registerWorkflowImplementationTypes(classOf[PorWorkflowImpl])
    worker.registerActivitiesImplementations(impl)

    factory.start()

    log.info(s"PoR Worker started and listening on task queue: $TASK_QUEUE")

    sys.addShutdownHook {
      log.info("Shutting down worker...")
      factory.shutdown()
      service.shutdown()
    }

    Thread.currentThread().join()
  }
}
