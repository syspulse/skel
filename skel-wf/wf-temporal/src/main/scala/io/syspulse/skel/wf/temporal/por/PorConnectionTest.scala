package io.syspulse.skel.wf.temporal.por

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.syspulse.skel.wf.temporal.ScalaDataConverter

object PorConnectionTest {
  def main(args: Array[String]): Unit = {
    println("Testing Temporal connection...")

    try {
      // Try simplest possible connection
      val service = WorkflowServiceStubs.newLocalServiceStubs()

      // Create client with Scala DataConverter
      val clientOptions = io.temporal.client.WorkflowClientOptions.newBuilder()
        .setDataConverter(ScalaDataConverter.create())
        .build()

      val client = WorkflowClient.newInstance(service, clientOptions)

      println("✓ Connection established successfully!")
      println(s"Client: $client")
      println(s"Service: $service")

      // Try to get system info
      println("\nTrying to query namespaces...")

      service.shutdown()
      println("\n✓ Test completed successfully!")

    } catch {
      case e: Exception =>
        println(s"\n✗ Connection failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}
