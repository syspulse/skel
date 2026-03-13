package io.syspulse.skel.wf.temporal

import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.{Success, Failure}

/**
 * Manual test program for Temporal init command
 *
 * Run with:
 *   sbt "project skel-wf-temporal" "test:runMain io.syspulse.skel.wf.temporal.TestInit"
 *
 * Or with arguments:
 *   sbt "project skel-wf-temporal" "test:runMain io.syspulse.skel.wf.temporal.TestInit tid:Int pid:Int sys:Keyword"
 */
object TestInit {
  implicit val ec: ExecutionContext = ExecutionContext.global

  def main(args: Array[String]): Unit = {
    println("=== Temporal Init Test Program ===\n")

    // Get Temporal URI from environment or use default
    val uri = sys.env.getOrElse("TEMPORAL_URI", "temporal://localhost:7233")
    println(s"Temporal URI: $uri")

    // Default attributes if no args provided
    val attributeSpecs = if (args.isEmpty) {
      Array("tid:Int", "pid:Int", "sys:Keyword")
    } else {
      args
    }

    println(s"Registering ${attributeSpecs.length} search attributes:")
    attributeSpecs.foreach(spec => println(s"  - $spec"))
    println()

    // Parse attribute specs
    val attributes = try {
      attributeSpecs.map { spec =>
        spec.split(":") match {
          case Array(name, attrType) => name -> attrType
          case _ =>
            System.err.println(s"ERROR: Invalid attribute spec: $spec (expected format: name:type)")
            System.err.println(s"Valid types: Int, Long, Keyword, Text, Bool, Datetime, Double, KeywordList")
            sys.exit(1)
        }
      }.toMap
    } catch {
      case e: Exception =>
        System.err.println(s"ERROR: Failed to parse attributes: ${e.getMessage}")
        sys.exit(1)
    }

    println("Parsed attributes:")
    attributes.foreach { case (name, attrType) =>
      println(s"  $name -> $attrType")
    }
    println()

    // Check if Temporal is accessible
    print("Checking Temporal connection... ")
    val connectionCheck = try {
      val temporal = new Temporal(uri)
      temporal.shutdown()
      println("✓ Connected")
      true
    } catch {
      case e: Exception =>
        println(s"✗ Failed")
        println(s"ERROR: Cannot connect to Temporal: ${e.getMessage}")
        println(s"\nMake sure Temporal server is running:")
        println(s"  temporal server start-dev")
        false
    }

    if (!connectionCheck) {
      sys.exit(1)
    }
    println()

    // Register attributes
    println("Registering search attributes...")
    val result = Temporal.registerSearchAttributes(uri, attributes)

    try {
      val counts = Await.result(result, 30.seconds)

      println("\nResults:")
      attributes.keys.foreach { name =>
        println(s"  ✓ $name")
      }

      println(s"\n✓ Successfully registered ${counts.size} search attributes")

      // Verification instructions
      println("\nTo verify registration:")
      println("  temporal operator search-attribute list --namespace default")
      println("\nTo query workflows using these attributes:")
      println("  temporal workflow list --query \"tid = 1\"")
      println("  temporal workflow list --query \"tid = 1 AND pid = 42\"")
      println("  temporal workflow list --query \"sys = 'haas'\"")

    } catch {
      case e: java.util.concurrent.TimeoutException =>
        System.err.println("\n✗ ERROR: Registration timed out after 30 seconds")
        System.err.println("This might indicate a problem with the Temporal server connection")
        sys.exit(1)

      case e: Exception =>
        System.err.println(s"\n✗ ERROR: Registration failed")
        System.err.println(s"Message: ${e.getMessage}")
        e.printStackTrace()
        sys.exit(1)
    }
  }
}
