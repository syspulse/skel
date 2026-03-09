package io.syspulse.skel.wf.temporal.por

import scala.util.Try

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.{Worker, WorkerFactory}
import io.syspulse.skel.wf.temporal.{ScalaDataConverter, TemporalURI}
import com.typesafe.scalalogging.Logger
import scala.util.Success

object PorWorker {
  private val log = Logger(getClass.getName)

  val TASK_QUEUE = "por-task-queue"

  def run(uri: String, impl: PorActivities): Try[Worker] = Try {
    val t = TemporalURI(uri)
    log.info(s"Connecting -> ${t.target} (namespace=${t.namespace})")

    val serviceOptions = io.temporal.serviceclient.WorkflowServiceStubsOptions.newBuilder()
      .setTarget(t.target)
      .setEnableKeepAlive(t.enableKeepAlive)
      .setKeepAliveTime(java.time.Duration.ofMillis(t.keepAliveTime))
      .setKeepAliveTimeout(java.time.Duration.ofMillis(t.keepAliveTimeout))
      .setRpcTimeout(java.time.Duration.ofMillis(t.rpcTimeout))
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

    log.info(s"Worker: namespace=${t.namespace}, task_queue=${TASK_QUEUE} (${worker})")

    sys.addShutdownHook {
      log.info(s"Shutdown: ${factory}, ${service}")
      factory.shutdown()
      service.shutdown()
    }
    
    worker
  }
}
