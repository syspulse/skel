package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

class ScriptFlowConcurrencySpec extends AnyWordSpec with Matchers {

  "ScriptFlow.exec" should {
    "run 100 ScriptFlow instances concurrently with ScriptSleepTest using Future composition" in {
      val random = new Random()
      
      // Create 100 ScriptFlow instances, each with 2-3 ScriptSleepTest scripts
      // Note: Using ScriptSleepTest only (not ScriptJS) to avoid GraalVM Polyglot native library
      // loading conflicts when creating multiple engines concurrently
      val flows = (1 to 100).map { _ =>
        val numScripts = 2 + random.nextInt(2) // Random 2 or 3 scripts per flow
        val sleepScripts = (1 to numScripts).map { _ =>
          val sleepTime = 10 + random.nextInt(91) // Random between 10-100 msec
          new ScriptSleepTest(sleepTime)
        }
        new ScriptFlow(sleepScripts)
      }
      
      val initialInput = "test"
      
      val startTime = System.currentTimeMillis()
      
      // Run all 100 flows concurrently using Future.sequence
      val futureResults = flows.map(flow => flow.exec("", initialInput, Map.empty))
      val allResultsFuture = Future.sequence(futureResults)
      
      // Verify Future creation is instant (non-blocking)
      val futureCreatedTime = System.currentTimeMillis()
      val futureCreationDuration = futureCreatedTime - startTime
      futureCreationDuration should be < 100L // Future creation should be nearly instantaneous
      
      // Wait for all flows to complete concurrently
      val results = Await.result(allResultsFuture, 3.seconds)
      val endTime = System.currentTimeMillis()
      val totalDuration = endTime - startTime
      
      // With concurrent execution of 100 flows, each with 2-3 scripts (max 100ms each),
      // all flows should complete within 1-2 seconds due to parallel execution
      totalDuration should be < 2000L // Should complete within 2 seconds
      totalDuration should be > 0L // Should take some time
      
      // Verify all flows completed successfully
      results.size shouldBe 100
      results.foreach { result =>
        result should not be null
        result should not be empty
      }
    }
  }
}

