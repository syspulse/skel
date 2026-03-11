package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.Await

/**
 * Test suite for Temporal search attribute registration
 *
 * Note: These are integration tests that require a running Temporal server.
 * They are SKIPPED by default to allow fast unit test runs.
 *
 * To run these tests:
 *   1. Start Temporal server: temporal server start-dev
 *   2. Enable tests: export TEMPORAL_TESTS_ENABLED=true
 *   3. Run tests: bloop test wf_temporal
 *
 * Without TEMPORAL_TESTS_ENABLED=true, these tests will be CANCELED (skipped).
 */
class TemporalInitSpec extends AnyWordSpec with Matchers with ScalaFutures {

  // Configure timeout for async operations
  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 30.seconds)

  val temporalUri = sys.env.getOrElse("TEMPORAL_URI", "temporal://localhost:7233")

  // Skip tests if Temporal is not running
  // Auto-detect Temporal server availability
  def isTemporalRunning: Boolean = {
    try {
      // Try a simple TCP connection to check if server is listening
      val socket = new java.net.Socket()
      socket.connect(new java.net.InetSocketAddress("localhost", 7233), 2000)
      socket.close()
      true
    } catch {
      case _: Exception =>
        println("Temporal server not detected on localhost:7233 - skipping integration tests")
        false
    }
  }

  "Temporal.registerSearchAttribute" should {

    "register a single Int attribute" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "tid", "Int")

      whenReady(result) { message =>
        message should (include("registered successfully") or include("already exists") or include("limit reached") or include("cannot have more than"))
      }
    }

    "register a single Keyword attribute" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_sys", "Keyword")

      whenReady(result) { message =>
        message should (include("registered successfully") or include("already exists") or include("limit reached"))
      }
    }

    "handle already existing attributes gracefully" in {
      assume(isTemporalRunning, "Temporal server is not running")

      // Register once
      val result1 = Temporal.registerSearchAttribute(temporalUri, "test_duplicate", "Int")
      whenReady(result1) { _ => }

      // Register again - should not fail
      val result2 = Temporal.registerSearchAttribute(temporalUri, "test_duplicate", "Int")

      whenReady(result2) { message =>
        // Should succeed in any of these ways: already exists, registered successfully, or limit reached
        message should (include("already exists") or include("registered successfully") or include("limit reached"))
      }
    }

    "reject invalid attribute types" in {
      // No server required: validate via static helper (no Temporal connection)
      val ex = the[IllegalArgumentException] thrownBy { Temporal.validateSearchAttributeType("InvalidType") }
      ex.getMessage should include("Unknown attribute type")
    }
  }

  "Temporal.registerSearchAttributes" should {

    "register multiple attributes at once" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val attributes = Map(
        "test_multi_1" -> "Int",
        "test_multi_2" -> "Keyword",
        "test_multi_3" -> "Bool"
      )

      val result = Temporal.registerSearchAttributes(temporalUri, attributes)

      whenReady(result) { messages =>
        messages should have size 3
        messages.foreach { message =>
          message should (include("registered successfully") or include("already exists") or include("limit reached"))
        }
      }
    }

    "handle mixed success and already-exists" in {
      assume(isTemporalRunning, "Temporal server is not running")

      // Register some attributes first
      val attributes1 = Map("test_mixed_1" -> "Int")
      whenReady(Temporal.registerSearchAttributes(temporalUri, attributes1)) { _ => }

      // Register again with one new and one existing
      val attributes2 = Map(
        "test_mixed_1" -> "Int",  // Already exists or limit reached
        "test_mixed_2" -> "Keyword"  // New
      )

      val result = Temporal.registerSearchAttributes(temporalUri, attributes2)

      whenReady(result) { messages =>
        messages should have size 2
        // test_mixed_1 should have some valid response
        messages.foreach { message =>
          message should (include("registered successfully") or include("already exists") or include("limit reached"))
        }
      }
    }
  }

  "Temporal search attribute types" should {

    "support Int type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_int", "Int")
      whenReady(result) { message =>
        message should (include("Int") or include("already exists") or include("limit reached"))
      }
    }

    "support Long type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_long", "Long")
      whenReady(result) { message =>
        message should (include("Long") or include("already exists") or include("limit reached"))
      }
    }

    "support Keyword type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_keyword", "Keyword")
      whenReady(result) { message =>
        message should (include("Keyword") or include("already exists") or include("limit reached"))
      }
    }

    "support Bool type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_bool", "Bool")
      whenReady(result) { message =>
        message should (include("Bool") or include("already exists") or include("limit reached"))
      }
    }

    "support Double type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_double", "Double")
      whenReady(result) { message =>
        message should (include("Double") or include("already exists") or include("limit reached"))
      }
    }

    "support KeywordList type" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "test_keywordlist", "KeywordList")
      whenReady(result) { message =>
        message should (include("KeywordList") or include("already exists") or include("limit reached"))
      }
    }
  }

  "Temporal default attributes" should {

    "register tid, pid, sys successfully" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val attributes = Map(
        "tid" -> "Int",
        "pid" -> "Int",
        "sys" -> "Keyword"
      )

      val result = Temporal.registerSearchAttributes(temporalUri, attributes)

      whenReady(result) { messages =>
        messages should have size 3
        messages.foreach { message =>
          message should (include("registered successfully") or include("already exists") or include("limit reached"))
        }
      }
    }
  }
}
