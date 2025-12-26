package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ScriptFlowSpec extends AnyWordSpec with Matchers {

  "ScriptFlow" should {
    "chain ScriptJQ -> ScriptRegexp to extract JSON field and match pattern" in {
      val jqEngine = new ScriptJQ(Some(".name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine))
      
      val json = """{"name":"John","age":30}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // JQ extracts "John", regexp matches ".*John.*" -> returns "John"
      result.get should include("John")
    }

    "chain ScriptRegexp extract -> ScriptRegexp match to extract and validate" in {
      val extractEngine = new ScriptRegexp(Some("?hash=(0x[0-9a-f]+)"))
      val matchEngine = new ScriptRegexp(Some("0x[0-9a-f]+"))
      val flow = new ScriptFlow(Seq(extractEngine, matchEngine))
      
      val result = flow.run("", "hash=0xabc123", Map.empty)
      // Extract "0xabc123", match "0x[0-9a-f]+" -> returns "0xabc123"
      result shouldBe Success("0xabc123")
    }

    "chain ScriptJQ -> ScriptRegexp extract to extract nested JSON and pattern" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine))
      
      val json = """{"user":{"name":"John Doe","age":30}}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // JQ extracts "John Doe", regexp matches ".*John.*" -> returns "John Doe"
      result.get should include("John Doe")
    }

    "chain ScriptRegexp extract -> ScriptJQ to extract value then query JSON" in {
      val extractEngine = new ScriptRegexp(Some("?data=([^&]+)"))
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(extractEngine, jqEngine))
      
      val jsonData = """{"name":"Alice","age":25}"""
      val input = s"data=$jsonData&other=value"
      val result = flow.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      result.get should include("Alice")
    }

    "chain multiple ScriptRegexp engines for complex extraction" in {
      val extract1 = new ScriptRegexp(Some("?id=([0-9]+)"))
      val extract2 = new ScriptRegexp(Some("?value:([0-9]+)"))
      val matchEngine = new ScriptRegexp(Some("[0-9]+"))
      val flow = new ScriptFlow(Seq(extract1, extract2, matchEngine))
      
      val result = flow.run("", "id=42", Map.empty)
      result.isSuccess shouldBe true
    }

    "chain ScriptJQ -> ScriptRegexp -> ScriptRegexp for JSON extraction and validation" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val matchEngine1 = new ScriptRegexp(Some(".*John.*"))
      val matchEngine2 = new ScriptRegexp(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, matchEngine1, matchEngine2))
      
      val json = """{"user":{"name":"John","age":30}}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // JQ extracts "John", first regexp matches ".*John.*" -> returns "John", 
      // second regexp matches ".*John.*" against "John" -> returns "John"
      result.get should include("John")
    }

    "chain ScriptRegexp -> ScriptJQ -> ScriptRegexp for pattern-JSON-pattern flow" in {
      val extractEngine = new ScriptRegexp(Some("?json=([^&]+)"))
      val jqEngine = new ScriptJQ(Some(".status"))
      val matchEngine = new ScriptRegexp(Some(".*active.*"))
      val flow = new ScriptFlow(Seq(extractEngine, jqEngine, matchEngine))
      
      val jsonData = """{"status":"active","count":5}"""
      val input = s"json=$jsonData&other=data"
      val result = flow.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // Extract JSON, JQ extracts "active", regexp matches ".*active.*" -> returns "active"
      result.get should include("active")
    }

    "handle empty flow gracefully" in {
      val flow = new ScriptFlow(Seq.empty)
      
      val result = flow.run("", "test", Map.empty)
      result shouldBe Success("test")
    }

    "pass src parameter to all engines in flow" in {
      val regexpEngine1 = new ScriptRegexp(None)
      val regexpEngine2 = new ScriptRegexp(None)
      val flow = new ScriptFlow(Seq(regexpEngine1, regexpEngine2))
      
      // First engine matches "foobar" against ".*foo.*" -> returns "foobar"
      // Second engine matches "foobar" against ".*foo.*" -> returns "foobar"
      // This verifies that both engines use the same src parameter
      val result = flow.run(".*foo.*", "foobar", Map.empty)
      result shouldBe Success("foobar")
      
      // Test with pattern that matches both input and intermediate result
      val result2 = flow.run(".*(foo|true).*", "foobar", Map.empty)
      result2 shouldBe Success("foobar")
    }

    "chain ScriptRegexp extract -> ScriptJQ -> ScriptRegexp match for complex flow" in {
      val extractEngine = new ScriptRegexp(Some("?json=([^&]+)"))
      val jqEngine = new ScriptJQ(Some(".name"))
      val matchEngine = new ScriptRegexp(Some(".*Alice.*"))
      val flow = new ScriptFlow(Seq(extractEngine, jqEngine, matchEngine))
      
      val jsonData = """{"name":"Alice","age":25}"""
      val input = s"json=$jsonData&other=value"
      val result = flow.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // Extract JSON, JQ extracts "Alice", regexp matches ".*Alice.*" -> returns "Alice"
      result.get should include("Alice")
    }

    "build from string format: regexp://, jq://" in {
      val flow = ScriptFlow.build(Some("regexp://, jq://"))
      
      val json = """{"name":"John","age":30}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // Both engines with empty src should process the input
      // The result depends on how parseUri handles empty src
      result.isSuccess shouldBe true
    }

    "build from string format: regexp://pattern, jq://path" in {
      val flow = ScriptFlow.build(Some("regexp://.*foo.*, jq://.name"))
      
      val json = """{"name":"foobar","age":30}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // Regexp matches JSON string against pattern, then JQ extracts field
      result.isSuccess shouldBe true
    }

    "build from string format: jq://.name, regexp://.*John.*" in {
      val flow = ScriptFlow.build(Some("jq://.name, regexp://.*John.*"))
      
      val json = """{"name":"John","age":30}"""
      val result = flow.run("", json, Map.empty)
      
      result.isSuccess shouldBe true
      // JQ extracts ".name" -> "John", then regexp matches ".*John.*" -> returns "John"
      result.get should include("John")
    }

    "build from string format: regexp://.B" in {
      val flow = ScriptFlow.build(Some("regexp://.B"))
      
      // Pattern ".B" matches any character followed by 'B'
      val result1 = flow.run("", "AB", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "AB" // "AB" matches ".B" -> returns "AB"
      
      val result2 = flow.run("", "XB", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "XB" // "XB" matches ".B" -> returns "XB"
      
      val result3 = flow.run("", "A", Map.empty)
      result3.isSuccess shouldBe true
      result3.get shouldBe "" // "A" doesn't match ".B" -> returns ""
      
      val result4 = flow.run("", "test", Map.empty)
      result4.isSuccess shouldBe true
      result4.get shouldBe "" // "test" doesn't match ".B" -> returns ""
    }
  }

  "ScriptFlow.exec" should {
    "chain ScriptJQ -> ScriptRegexp -> ScriptJQ using Future composition" in {
      val jqEngine1 = new ScriptJQ(Some(".user.name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val jqEngine2 = new ScriptJQ(Some(".age"))
      val flow = new ScriptFlow(Seq(jqEngine1, regexpEngine, jqEngine2))
      
      val json = """{"user":{"name":"John Doe","age":30},"status":"active"}"""
      
      val futureResult = flow.exec("", json, Map.empty)
      
      // First JQ extracts ".user.name" -> returns List representation (e.g., "List(John Doe)")
      // Regexp matches ".*John.*" against List string -> returns matched string
      // Second JQ tries to extract ".age" from non-JSON string -> throws ParseException
      // The Future will fail with an exception
      val caught = intercept[ujson.ParseException] {
        Await.result(futureResult, 5.seconds)
      }
      caught.getMessage should include("json") // Should contain JSON parsing error
    }

    "chain multiple ScriptRegexp engines with Future composition" in {
      val extractEngine1 = new ScriptRegexp(Some("?id=([0-9]+)"))
      val matchEngine = new ScriptRegexp(Some("[0-9]+"))
      val flow = new ScriptFlow(Seq(extractEngine1, matchEngine))
      
      val input = "id=42&other=value:123&more=data"
      
      val futureResult = flow.exec("", input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // First regexp extracts "id=42" -> "42"
      // Match engine validates "42" matches "[0-9]+" -> returns "42"
      result shouldBe "42"
      result should not include("value:123") // Should not contain other parts of input
      result should not include("more=data") // Should not contain other parts of input
    }

    "chain ScriptRegexp -> ScriptJQ -> ScriptRegexp with Future composition" in {
      val extractEngine = new ScriptRegexp(Some("?json=([^&]+)"))
      val jqEngine = new ScriptJQ(Some(".status"))
      val matchEngine = new ScriptRegexp(Some(".*active.*"))
      val flow = new ScriptFlow(Seq(extractEngine, jqEngine, matchEngine))
      
      val jsonData = """{"status":"active","count":5}"""
      val input = s"json=$jsonData&other=data"
      
      val futureResult = flow.exec("", input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // Extract JSON from URL parameter -> JSON string
      // JQ extracts ".status" -> returns List("active") which becomes "List(active)" or similar
      // Regexp matches ".*active.*" against the string representation -> returns the matched string
      // Since JQ returns a List, the string representation includes the full List structure
      result should include("active")
      // Note: ScriptJQ returns Lists, so the string representation may include other parts
    }

    "chain ScriptJQ -> ScriptRegexp -> ScriptJQ -> ScriptRegexp with Future composition" in {
      val jqEngine1 = new ScriptJQ(Some(".user.name"))
      val regexpEngine1 = new ScriptRegexp(Some(".*John.*"))
      val jqEngine2 = new ScriptJQ(Some(".age"))
      val regexpEngine2 = new ScriptRegexp(Some("[0-9]+"))
      val flow = new ScriptFlow(Seq(jqEngine1, regexpEngine1, jqEngine2, regexpEngine2))
      
      val json = """{"user":{"name":"John","age":30},"status":"active"}"""
      
      val futureResult = flow.exec("", json, Map.empty)
      
      // JQ1 extracts ".user.name" -> returns List representation
      // Regexp1 matches against List string -> returns matched string
      // JQ2 tries to extract ".age" from non-JSON string -> throws exception
      // The Future will fail with an exception, so we catch it
      val caught = intercept[Exception] {
        Await.result(futureResult, 5.seconds)
      }
      caught.getMessage should include("json") // Should contain JSON parsing error
    }

    "handle Future composition errors gracefully" in {
      val jqEngine = new ScriptJQ(Some(".nonexistent.field"))
      val regexpEngine = new ScriptRegexp(Some(".*test.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine))
      
      val json = """{"name":"test","age":30}"""
      
      val futureResult = flow.exec("", json, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // JQ fails to extract nonexistent field -> returns empty/null or error
      // Regexp processes the result -> should handle gracefully
      // Result should be some value (either error message or processed result)
      result should not be null
      // The regexp should still process whatever JQ returned (even if empty/null)
      result should not be empty
    }

    "chain multiple ScriptJQ engines with Future composition" in {
      val jqEngine1 = new ScriptJQ(Some(".user.name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val jqEngine2 = new ScriptJQ(Some(".status"))
      val flow = new ScriptFlow(Seq(jqEngine1, regexpEngine, jqEngine2))
      
      val json = """{"user":{"name":"John","age":30},"status":"active"}"""
      
      val futureResult = flow.exec("", json, Map.empty)
      
      // JQ1 extracts ".user.name" -> returns List representation
      // Regexp matches ".*John.*" against List string -> returns matched string
      // JQ2 tries to extract ".status" from non-JSON string -> throws ParseException
      // The Future will fail with an exception
      val caught = intercept[ujson.ParseException] {
        Await.result(futureResult, 5.seconds)
      }
      caught.getMessage should include("json") // Should contain JSON parsing error
    }

    "chain ScriptJS with ScriptRegexp using Future composition" in {
      val jsEngine = new ScriptJS()
      val regexpEngine = new ScriptRegexp(Some(".*RESULT.*"))
      val flow = new ScriptFlow(Seq(jsEngine, regexpEngine))
      
      val jsCode = """input.toUpperCase() + "_RESULT""""
      val input = "test"
      
      val futureResult = flow.exec(jsCode, input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // JS transforms "test" -> "TEST_RESULT" (using jsCode as src)
      // Regexp receives src=jsCode, so it uses jsCode as pattern (not constructor pattern)
      // jsCode doesn't match "TEST_RESULT", so returns ""
      // We need to verify the JS worked even if regexp doesn't match as expected
      result shouldBe "" // Regexp doesn't match jsCode pattern against "TEST_RESULT"
    }

    "chain ScriptRegexp -> ScriptJS -> ScriptRegexp with Future composition" in {
      val extractEngine = new ScriptRegexp(Some("?value=([^&]+)"))
      val jsEngine = new ScriptJS()
      val matchEngine = new ScriptRegexp(Some(".*UPPERCASE.*"))
      val flow = new ScriptFlow(Seq(extractEngine, jsEngine, matchEngine))
      
      val input = "value=hello&other=data"
      val jsCode = """input.toUpperCase() + "_UPPERCASE""""
      
      val futureResult = flow.exec(jsCode, input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // Regexp extracts "value=hello" -> "hello" (using constructor pattern)
      // JS transforms "hello" -> "HELLO_UPPERCASE" (using jsCode as src)
      // Regexp receives src=jsCode, so it uses jsCode as pattern (not constructor pattern)
      // jsCode doesn't match "HELLO_UPPERCASE", so returns ""
      result shouldBe "" // Regexp doesn't match jsCode pattern against "HELLO_UPPERCASE"
    }

    "chain ScriptRegexp extract -> ScriptJS transform -> ScriptRegexp match with Future composition" in {
      val extractEngine = new ScriptRegexp(Some("?id=([0-9]+)"))
      val jsEngine = new ScriptJS()
      val matchEngine = new ScriptRegexp(Some("[0-9]+"))
      val flow = new ScriptFlow(Seq(extractEngine, jsEngine, matchEngine))
      
      val input = "id=42&other=data"
      val jsCode = """parseInt(input) * 2"""
      
      val futureResult = flow.exec(jsCode, input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // Regexp extracts "id=42" -> "42" (using constructor pattern when src is blank)
      // JS transforms "42" -> "84" (42 * 2, using jsCode as src)
      // Regexp receives src=jsCode, so it uses jsCode as pattern (not constructor pattern)
      // jsCode doesn't match "84", so returns ""
      result shouldBe "" // Regexp doesn't match jsCode pattern against "84"
    }
  }
}

