package io.syspulse.skel.wf.temporal.por

import io.syspulse.skel.wf.temporal.ScalaDataConverter

object PorSerializationTest {

  def main(args: Array[String]): Unit = {
    println("Testing Jackson serialization with Scala case classes...")

    val dataConverter = ScalaDataConverter.create()

    // Test PorWorkflowInput serialization
    val input = PorWorkflowInput(
      cexName = "TestExchange",
      timestamp = System.currentTimeMillis(),
      pooRequired = true,
      porRequired = true,
      polRequired = true,
      reportRequired = true
    )

    try {
      println(s"\nOriginal input: $input")

      // Serialize to Temporal payload
      val payload = dataConverter.toPayload(input).get()
      println(s"✓ Serialization successful")
      println(s"  Payload size: ${payload.getData.size()} bytes")

      // Deserialize back
      val deserialized = dataConverter.fromPayload(payload, classOf[PorWorkflowInput], classOf[PorWorkflowInput])
      println(s"✓ Deserialization successful")
      println(s"  Deserialized: $deserialized")

      // Verify equality
      if (input == deserialized) {
        println(s"✓ Values match!")
      } else {
        println(s"✗ Values don't match!")
        println(s"  Expected: $input")
        println(s"  Got:      $deserialized")
      }

      println("\n✓ All serialization tests passed!")

    } catch {
      case e: Exception =>
        println(s"\n✗ Serialization test failed: ${e.getMessage}")
        e.printStackTrace()
    }
  }
}
