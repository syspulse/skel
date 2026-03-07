package io.syspulse.skel.wf.temporal.por

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.{Worker, WorkerFactory}
import io.syspulse.skel.wf.temporal.ScalaDataConverter

object PorWorker {

  val TASK_QUEUE = "por-task-queue"

  def main(args: Array[String]): Unit = {
    // Get Temporal service address from environment or use default
    val temporalServiceAddress = sys.env.getOrElse("TEMPORAL_SERVICE_ADDRESS", "127.0.0.1:7233")

    println(s"Connecting to Temporal service at: $temporalServiceAddress")

    // Create service stub - use local service stubs for development
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

    // Create worker factory
    val factory = WorkerFactory.newInstance(client)

    // Create worker for the task queue
    val worker = factory.newWorker(TASK_QUEUE)

    // Register workflow implementation
    worker.registerWorkflowImplementationTypes(classOf[PorWorkflowImpl])

    // Register activities implementation
    worker.registerActivitiesImplementations(new PorActivitiesImpl())

    // Start all workers
    factory.start()

    println(s"PoR Worker started and listening on task queue: $TASK_QUEUE")
    println("Press Ctrl+C to stop...")

    // Keep the worker running
    sys.addShutdownHook {
      println("\nShutting down worker...")
      factory.shutdown()
      service.shutdown()
    }

    // Wait indefinitely
    Thread.currentThread().join()
  }
}
