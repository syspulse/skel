package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
 * Simplified test suite for Temporal search attribute registration
 *
 * Tests only the main attributes (tid, pid, sys) to avoid hitting
 * Temporal dev server limits (max 3 attributes per type).
 *
 * These tests auto-detect if Temporal server is running and skip if not.
 */
class TemporalInitSimpleSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 30.seconds)

  val temporalUri = sys.env.getOrElse("TEMPORAL_URI", "temporal://localhost:7233")

  // Auto-detect Temporal server availability
  def isTemporalRunning: Boolean = {
    try {
      val socket = new java.net.Socket()
      socket.connect(new java.net.InetSocketAddress("localhost", 7233), 2000)
      socket.close()
      true
    } catch {
      case _: Exception =>
        info("Temporal server not detected on localhost:7233 - skipping integration tests")
        false
    }
  }

  "Temporal.registerSearchAttribute" should {

    "register tid (Int) attribute" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "tid", "Int")

      whenReady(result) { message =>
        message should (include("registered successfully") or include("already exists") or include("limit reached"))
      }
    }

    "register pid (Int) attribute" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "pid", "Int")

      whenReady(result) { message =>
        message should (include("registered successfully") or include("already exists") or include("limit reached"))
      }
    }

    "register sys (Keyword) attribute" in {
      assume(isTemporalRunning, "Temporal server is not running")

      val result = Temporal.registerSearchAttribute(temporalUri, "sys", "Keyword")

      whenReady(result) { message =>
        message should (include("registered successfully") or include("already exists") or include("limit reached"))
      }
    }

    "reject invalid attribute types" in {
      // No server required - this is pure validation
      val ex = the[IllegalArgumentException] thrownBy {
        Temporal.validateSearchAttributeType("InvalidType")
      }
      ex.getMessage should include("Unknown attribute type")
    }
  }

  "Temporal.registerSearchAttributes" should {

    "register all default attributes (tid, pid, sys)" in {
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

  "Temporal attribute type validation" should {

    "support Int type" in {
      val indexedType = Temporal.validateSearchAttributeType("Int")
      indexedType should not be null
    }

    "support Long type" in {
      val indexedType = Temporal.validateSearchAttributeType("Long")
      indexedType should not be null
    }

    "support Keyword type" in {
      val indexedType = Temporal.validateSearchAttributeType("Keyword")
      indexedType should not be null
    }

    "support Bool type" in {
      val indexedType = Temporal.validateSearchAttributeType("Bool")
      indexedType should not be null
    }

    "support Double type" in {
      val indexedType = Temporal.validateSearchAttributeType("Double")
      indexedType should not be null
    }

    "support KeywordList type" in {
      val indexedType = Temporal.validateSearchAttributeType("KeywordList")
      indexedType should not be null
    }

    "reject invalid types" in {
      the[IllegalArgumentException] thrownBy {
        Temporal.validateSearchAttributeType("InvalidType")
      }
    }
  }
}
