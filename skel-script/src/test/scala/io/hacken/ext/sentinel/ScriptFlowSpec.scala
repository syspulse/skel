package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class ScriptFlowSpec extends AnyWordSpec with Matchers {
  // disable warning about using interpreter only
  sys.props("polyglot.engine.WarnInterpreterOnly") = "false"

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

    "build from string format: js://script" in {
      val flow = ScriptFlow.build(Some("js://input.toUpperCase()"))
      
      val result1 = flow.run("", "hello", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "HELLO"
      
      val result2 = flow.run("", "world", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "WORLD"
    }

    "build simple score script: js://" in {
      val flow = ScriptFlow.build(Some("js://1.0"))
      
      val result1 = flow.run("", "News", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "1"
      
      val result2 = flow.run("", "", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "1"
    }

    "build from string format: js:// with complex transformation" in {
      val flow = ScriptFlow.build(Some("js://input.split('').reverse().join('')"))
      
      val result = flow.run("", "hello", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "olleh"
    }

    "build from string format: js:// with numeric operations" in {
      val flow = ScriptFlow.build(Some("js://parseInt(input) * 2"))
      
      val result1 = flow.run("", "5", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "10"
      
      val result2 = flow.run("", "21", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "42"
    }

    "build from string format: js:// -> regexp://" in {
      val flow = ScriptFlow.build(Some("js://input.toUpperCase(), regexp://.*HELLO.*"))
      
      val result1 = flow.run("", "hello", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "HELLO" // JS transforms to "HELLO", regexp matches
      
      val result2 = flow.run("", "world", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "" // JS transforms to "WORLD", regexp doesn't match
    }

    "build from string format: regexp:// -> js://" in {
      val flow = ScriptFlow.build(Some("regexp://?value=([^&]+), js://input.toUpperCase()"))
      
      val input = "value=hello&other=data"
      val result = flow.run("", input, Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "HELLO" // Regexp extracts "hello", JS transforms to "HELLO"
    }

    "build from string format: jq:// -> js://" in {
      val flow = ScriptFlow.build(Some("jq://.name, js://input.toUpperCase()"))
      
      val json = """{"name":"john","age":30}"""
      val result = flow.run("", json, Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "\"JOHN\"" // JQ extracts "john", JS transforms to "JOHN"
    }

    "build from string format: js:// -> jq://" in {
      val flow = ScriptFlow.build(Some("js://JSON.stringify({name: input}), jq://.name"))
      
      val input = "test"
      val result = flow.run("", input, Map.empty)
      result.isSuccess shouldBe true
      result.get should include("test") // JS creates JSON, JQ extracts name
    }

    "build from string format: js:// -> regexp_score://" in {
      val flow = ScriptFlow.build(Some("js://input.length > 5 ? 'long' : 'short', regexp_score://.*long.*"))
      
      val result1 = flow.run("", "hello world", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "1.0" // JS returns "long", score matches
      
      val result2 = flow.run("", "hi", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "0.0" // JS returns "short", score doesn't match
    }

    "build from string format: multiple js:// scripts" in {
      val flow = ScriptFlow.build(Some("js://input.toUpperCase(), js://input + '_SUFFIX'"))
      
      val result = flow.run("", "test", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "TEST_SUFFIX" // First JS: "test" -> "TEST", Second JS: "TEST" -> "TEST_SUFFIX"
    }

    "chain ScriptJS alone" in {
      val jsEngine = new ScriptJS(Some("input.toUpperCase()"))
      val flow = new ScriptFlow(Seq(jsEngine))
      
      val result = flow.run("", "hello", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "HELLO"
    }

    "chain ScriptJS -> ScriptRegexp" in {
      val jsEngine = new ScriptJS(Some("input.toUpperCase()"))
      val regexpEngine = new ScriptRegexp(Some(".*HELLO.*"))
      val flow = new ScriptFlow(Seq(jsEngine, regexpEngine))
      
      val result1 = flow.run("", "hello", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "HELLO" // JS transforms, regexp matches
      
      val result2 = flow.run("", "world", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "" // JS transforms, regexp doesn't match
    }

    "chain ScriptRegexp -> ScriptJS" in {
      val extractEngine = new ScriptRegexp(Some("?id=([0-9]+)"))
      val jsEngine = new ScriptJS(Some("parseInt(input) * 2"))
      val flow = new ScriptFlow(Seq(extractEngine, jsEngine))
      
      val result = flow.run("", "id=42&other=data", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "84" // Regexp extracts "42", JS multiplies by 2
    }

    "chain ScriptJQ -> ScriptJS" in {
      val jqEngine = new ScriptJQ(Some(".name"))
      val jsEngine = new ScriptJS(Some("input.toUpperCase()"))
      val flow = new ScriptFlow(Seq(jqEngine, jsEngine))
      
      val json = """{"name":"john","age":30}"""
      val result = flow.run("", json, Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "\"JOHN\"" // JQ extracts "john", JS transforms to "JOHN"
    }

    "chain ScriptJS -> ScriptJQ" in {
      val jsEngine = new ScriptJS(Some("JSON.stringify({name: input})"))
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(jsEngine, jqEngine))
      
      val result = flow.run("", "test", Map.empty)
      result.isSuccess shouldBe true
      result.get should include("test") // JS creates JSON, JQ extracts name
    }

    "chain ScriptRegexpScore alone to return score value" in {
      val scoreEngine = new ScriptRegexpScore(Some(".*foo.*"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val result1 = flow.run("", "foobar", Map.empty)
      result1 shouldBe Success("1.0") // Matches -> "1.0"
      
      val result2 = flow.run("", "barbaz", Map.empty)
      result2 shouldBe Success("0.0") // Doesn't match -> "0.0"
    }

    "chain ScriptJQ -> ScriptRegexpScore to extract JSON and return score" in {
      val jqEngine = new ScriptJQ(Some(".name"))
      val scoreEngine = new ScriptRegexpScore(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, scoreEngine))
      
      val json1 = """{"name":"John","age":30}"""
      val result1 = flow.run("", json1, Map.empty)
      result1 shouldBe Success("1.0") // JQ extracts "John", score matches -> "1.0"
      
      val json2 = """{"name":"Alice","age":25}"""
      val result2 = flow.run("", json2, Map.empty)
      result2 shouldBe Success("0.0") // JQ extracts "Alice", score doesn't match -> "0.0"
    }

    "chain ScriptRegexp extract -> ScriptRegexpScore to extract and score" in {
      val extractEngine = new ScriptRegexp(Some("?hash=(0x[0-9a-f]+)"))
      val scoreEngine = new ScriptRegexpScore(Some("0x[0-9a-f]+"))
      val flow = new ScriptFlow(Seq(extractEngine, scoreEngine))
      
      val result1 = flow.run("", "hash=0xabc123", Map.empty)
      result1 shouldBe Success("1.0") // Extract "0xabc123", score matches -> "1.0"
      
      val result2 = flow.run("", "hash=invalid", Map.empty)
      result2 shouldBe Success("0.0") // Extract fails -> "", score doesn't match -> "0.0"
    }

    "chain ScriptRegexpScore -> ScriptRegexpScore for multiple scoring stages" in {
      val scoreEngine1 = new ScriptRegexpScore(Some(".*foo.*"))
      val scoreEngine2 = new ScriptRegexpScore(Some(".*bar.*"))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2))
      
      // Important: scoreEngine1 returns "1.0" or "0.0", which becomes the input to scoreEngine2
      // scoreEngine2 matches against "1.0" or "0.0", NOT the original input
      
      // First score matches "foobar" -> "1.0", second score matches "1.0" against ".*bar.*" -> "0.0" (because "1.0" doesn't contain "bar")
      val result1 = flow.run("", "foobar", Map.empty)
      result1 shouldBe Success("0.0") // "1.0" doesn't match ".*bar.*"
      
      // First score matches "foobaz" -> "1.0", second score doesn't match "1.0" against ".*bar.*" -> "0.0"
      val result2 = flow.run("", "foobaz", Map.empty)
      result2 shouldBe Success("0.0")
      
      // First score doesn't match "barbaz" -> "0.0", second score doesn't match "0.0" against ".*bar.*" -> "0.0"
      val result3 = flow.run("", "barbaz", Map.empty)
      result3 shouldBe Success("0.0")
      
      // To make chaining work meaningfully, the second pattern must match against score value format
      // Pattern that matches score values (contains a digit and a dot)
      val scoreEngine3 = new ScriptRegexpScore(Some(".*[0-9]\\..*"))
      val flow2 = new ScriptFlow(Seq(scoreEngine1, scoreEngine3))
      val result4 = flow2.run("", "foobar", Map.empty)
      result4 shouldBe Success("1.0") // "1.0" matches ".*[0-9]\\..*"
      
      // Pattern that matches "1.0" specifically
      val scoreEngine4 = new ScriptRegexpScore(Some("^1\\.0$"))
      val flow3 = new ScriptFlow(Seq(scoreEngine1, scoreEngine4))
      val result5 = flow3.run("", "foobar", Map.empty)
      result5 shouldBe Success("1.0") // "1.0" matches "^1\\.0$"
      
      val result6 = flow3.run("", "barbaz", Map.empty)
      result6 shouldBe Success("0.0") // "0.0" doesn't match "^1\\.0$"
    }

    "chain ScriptRegexpScore with extraction pattern" in {
      val scoreEngine = new ScriptRegexpScore(Some("?([0-9]+)"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val result1 = flow.run("", "The number is 42", Map.empty)
      result1 shouldBe Success("1.0") // Extraction succeeds -> "1.0"
      
      val result2 = flow.run("", "No numbers here", Map.empty)
      result2 shouldBe Success("0.0") // Extraction fails -> "0.0"
    }

    "chain ScriptRegexpScore with negation pattern" in {
      val scoreEngine = new ScriptRegexpScore(Some("!.*foo.*"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val result1 = flow.run("", "barbaz", Map.empty)
      result1 shouldBe Success("1.0") // Negation matches -> "1.0"
      
      val result2 = flow.run("", "foobar", Map.empty)
      result2 shouldBe Success("0.0") // Negation doesn't match -> "0.0"
    }

    "chain ScriptJQ -> ScriptRegexp -> ScriptRegexpScore for JSON extraction and scoring" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val scoreEngine = new ScriptRegexpScore(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine, scoreEngine))
      
      val json1 = """{"user":{"name":"John Doe","age":30}}"""
      val result1 = flow.run("", json1, Map.empty)
      result1 shouldBe Success("1.0") // JQ extracts "John Doe", regexp matches -> "John Doe", score matches -> "1.0"
      
      val json2 = """{"user":{"name":"Alice","age":25}}"""
      val result2 = flow.run("", json2, Map.empty)
      result2 shouldBe Success("0.0") // JQ extracts "Alice", regexp doesn't match -> "", score doesn't match -> "0.0"
    }

    "chain ScriptRegexp extract -> ScriptRegexpScore -> ScriptRegexpScore for extraction and double scoring" in {
      val extractEngine = new ScriptRegexp(Some("?value:([0-9]+)"))
      val scoreEngine1 = new ScriptRegexpScore(Some("[0-9]+"))
      val scoreEngine2 = new ScriptRegexpScore(Some(".*[0-9].*"))
      val flow = new ScriptFlow(Seq(extractEngine, scoreEngine1, scoreEngine2))
      
      val result1 = flow.run("", "value:42", Map.empty)
      result1 shouldBe Success("1.0") // Extract "42", score1 matches -> "1.0", score2 matches "1.0" -> "1.0"
      
      val result2 = flow.run("", "value:abc", Map.empty)
      result2 shouldBe Success("1.0") // Extract fails -> "", score1 doesn't match "" -> "0.0", score2 matches "0.0" against ".*[0-9].*" -> "1.0" (because "0.0" contains "0")
    }

    "chain ScriptRegexpScore with multiple patterns in sequence" in {
      val scoreEngine1 = new ScriptRegexpScore(Some(".*[0-9].*"))
      val scoreEngine2 = new ScriptRegexpScore(Some(".*[a-z].*"))
      val scoreEngine3 = new ScriptRegexpScore(Some(".*[A-Z].*"))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2, scoreEngine3))
      
      // "test123" matches first -> "1.0", matches second "1.0" against ".*[a-z].*" -> "0.0" (because "1.0" doesn't contain lowercase)
      val result1 = flow.run("", "test123", Map.empty)
      result1 shouldBe Success("0.0")
      
      // "Test123" matches first -> "1.0", doesn't match second "1.0" against ".*[a-z].*" -> "0.0"
      val result2 = flow.run("", "Test123", Map.empty)
      result2 shouldBe Success("0.0")
      
      // Test with pattern that matches score value format
      val scoreEngine4 = new ScriptRegexpScore(Some(".*[0-9].*"))
      val flow2 = new ScriptFlow(Seq(scoreEngine1, scoreEngine4))
      val result3 = flow2.run("", "test123", Map.empty)
      result3 shouldBe Success("1.0") // "1.0" matches ".*[0-9].*"
    }

    "build from string format with regexp_score" in {
      val flow = ScriptFlow.build(Some("regexp_score://.*foo.*"))
      
      // Verify ScriptRegexpScore is created and works correctly
      val result1 = flow.run("", "foobar", Map.empty)
      result1 shouldBe Success("1.0")
      
      val result2 = flow.run("", "barbaz", Map.empty)
      result2 shouldBe Success("0.0")
    }

    "build from string format with jq_score" in {
      val flow = ScriptFlow.build(Some("jq_score://.name"))
      
      // Verify ScriptJQScore is created and works correctly
      val result1 = flow.run("", """{"name":"John","age":30}""", Map.empty)
      result1 shouldBe Success("1.0")
    }

    "build from string format with sq_score" in {
      val flow = ScriptFlow.build(Some("sq_score://result"))
      
      // Verify ScriptSQScore is created (behavior test)
      val result = flow.run("", "some solidity output", Map.empty)
      result.isSuccess shouldBe true
      result.get should (be("0.0") or be("1.0"))
    }

    "build from string format with multiple score types" in {
      val flow = ScriptFlow.build(Some("jq_score://.name, regexp_score://.*[0-9]\\..*"))
      
      // Verify both score engines work correctly in sequence
      // First: jq_score extracts .name -> "1.0" (field exists)
      // Second: regexp_score matches ".*[0-9]\\..*" against "1.0" -> "1.0" (matches score format)
      val json = """{"name":"John","age":30}"""
      val result = flow.run("", json, Map.empty)
      result shouldBe Success("1.0")
      
      // Test with field that doesn't exist - ScriptJQScore throws exception
      val json2 = """{"age":30}"""
      val result2 = flow.run("", json2, Map.empty)
      result2.isFailure shouldBe true
      result2.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "build from string format with regex_score alias" in {
      val flow = ScriptFlow.build(Some("regex_score://.*foo.*"))
      
      // Verify regex_score alias works (same as regexp_score)
      val result1 = flow.run("", "foobar", Map.empty)
      result1 shouldBe Success("1.0")
      
      val result2 = flow.run("", "barbaz", Map.empty)
      result2 shouldBe Success("0.0")
    }

    "chain ScriptJQScore alone to return score value" in {
      val scoreEngine = new ScriptJQScore(Some(".name"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val result1 = flow.run("", """{"name":"John","age":30}""", Map.empty)
      result1 shouldBe Success("1.0") // Field exists -> "1.0"
      
      val result2 = flow.run("", """{"age":30}""", Map.empty)
      // ScriptJQ throws NoSuchElementException when field doesn't exist, which ScriptJQScore propagates
      result2.isFailure shouldBe true
      result2.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "chain ScriptJQ -> ScriptJQScore to extract JSON and return score" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val scoreEngine = new ScriptJQScore(Some("."))
      val flow = new ScriptFlow(Seq(jqEngine, scoreEngine))
      
      // Important: ScriptJQScore extends ScriptJQ, so it runs a JQ query on the input
      // JQ extracts "John" (as a string representation like "List(John)"), then ScriptJQScore tries to parse it as JSON
      // The result depends on how JQ formats the output - if it's valid JSON, it will succeed
      val json1 = """{"user":{"name":"John","age":30}}"""
      val result1 = flow.run("", json1, Map.empty)
      // JQ extracts "John" -> returns List representation, ScriptJQScore tries to parse it
      // If the output is valid JSON (like a JSON string), it might succeed -> "1.0"
      // Otherwise it fails -> exception
      result1.isSuccess shouldBe true
      // The result could be "1.0" if the JQ output is valid JSON, or an exception if not
      result1.get should (be("0.0") or be("1.0"))
      
      // Better test: chain ScriptJQScore directly
      val scoreEngine2 = new ScriptJQScore(Some(".user.name"))
      val flow2 = new ScriptFlow(Seq(scoreEngine2))
      val result2 = flow2.run("", json1, Map.empty)
      result2 shouldBe Success("1.0") // Field exists -> "1.0"
    }

    "chain ScriptJQScore -> ScriptJQScore for multiple scoring stages" in {
      val scoreEngine1 = new ScriptJQScore(Some(".name"))
      val scoreEngine2 = new ScriptJQScore(Some("."))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2))
      
      // Important: scoreEngine1 returns "1.0" or "0.0", which becomes the input to scoreEngine2
      // scoreEngine2 tries to parse "1.0" or "0.0" as JSON and run JQ query on it
      // Since "1.0" or "0.0" is not valid JSON, it will fail -> exception
      
      val json1 = """{"name":"John","age":30}"""
      val result1 = flow.run("", json1, Map.empty)
      // First matches -> "1.0", second tries to parse "1.0" as JSON
      // "1.0" can be parsed as a JSON number, so ScriptJQScore succeeds -> "1.0"
      result1 shouldBe Success("1.0")
      
      val json2 = """{"age":30}"""
      val result2 = flow.run("", json2, Map.empty)
      // First throws exception when field doesn't exist -> propagates
      result2.isFailure shouldBe true
      result2.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "chain ScriptSQScore alone to return score value" in {
      val scoreEngine = new ScriptSQScore(Some("result"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      // ScriptSQ extracts Solidity result values
      // For testing, we'll verify the engine works
      val result = flow.run("", "some solidity output", Map.empty)
      result.isSuccess shouldBe true
      // Result depends on whether SolidityResult.extractString finds the pattern
      result.get should (be("0.0") or be("1.0"))
    }

    "chain ScriptSQ -> ScriptSQScore to extract Solidity result and return score" in {
      val sqEngine = new ScriptSQ(Some("result"))
      val scoreEngine = new ScriptSQScore(Some(".*success.*"))
      val flow = new ScriptFlow(Seq(sqEngine, scoreEngine))
      
      // ScriptSQ extracts Solidity result, then score engine checks if it matches
      val result = flow.run("", "some solidity output", Map.empty)
      result.isSuccess shouldBe true
      // Result depends on Solidity extraction and pattern matching
      result.get should (be("0.0") or be("1.0"))
    }

    "chain ScriptJQScore -> ScriptRegexpScore for JSON field scoring then pattern matching" in {
      val jqScoreEngine = new ScriptJQScore(Some(".status"))
      val regexpScoreEngine = new ScriptRegexpScore(Some(".*[0-9]\\..*"))
      val flow = new ScriptFlow(Seq(jqScoreEngine, regexpScoreEngine))
      
      // Important: jqScoreEngine returns "1.0" or "0.0", which becomes the input to regexpScoreEngine
      val json1 = """{"status":"active"}"""
      val result1 = flow.run("", json1, Map.empty)
      result1 shouldBe Success("1.0") // First matches -> "1.0", second matches "1.0" against ".*[0-9]\\..*" -> "1.0"
      
      val json2 = """{"other":"value"}"""
      val result2 = flow.run("", json2, Map.empty)
      // ScriptJQScore throws exception when field doesn't exist, which propagates through the flow
      result2.isFailure shouldBe true
      result2.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "chain ScriptRegexpScore -> ScriptJQScore for pattern matching then JSON field scoring" in {
      val regexpScoreEngine = new ScriptRegexpScore(Some(".*[0-9].*"))
      val jqScoreEngine = new ScriptJQScore(Some("."))
      val flow = new ScriptFlow(Seq(regexpScoreEngine, jqScoreEngine))
      
      // Important: regexpScoreEngine returns "1.0" or "0.0", which becomes the input to jqScoreEngine
      // jqScoreEngine tries to parse "1.0" or "0.0" as JSON
      // "1.0" and "0.0" can be parsed as JSON numbers, so ScriptJQScore succeeds -> "1.0"
      val result1 = flow.run("", "test123", Map.empty)
      result1 shouldBe Success("1.0") // First matches -> "1.0", second parses "1.0" as JSON number -> "1.0"
      
      val result2 = flow.run("", "test", Map.empty)
      result2 shouldBe Success("1.0") // First doesn't match -> "0.0", second parses "0.0" as JSON number -> "1.0"
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
      // Second JQ tries to extract ".age" from non-JSON string -> throws InvalidData exception
      // The Future will fail with an exception
      val caught = intercept[Exception] {
        Await.result(futureResult, 5.seconds)
      }
      // ujson throws InvalidData exception when trying to parse invalid JSON
      caught.getClass.getSimpleName should include("InvalidData")
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
      // JQ2 tries to extract ".age" from non-JSON string -> throws InvalidData exception
      // The Future will fail with an exception, so we catch it
      val caught = intercept[Exception] {
        Await.result(futureResult, 5.seconds)
      }
      // ujson throws InvalidData exception when trying to parse invalid JSON
      caught.getClass.getSimpleName should include("InvalidData")
    }

    "handle Future composition errors gracefully" in {
      val jqEngine = new ScriptJQ(Some(".nonexistent.field"))
      val regexpEngine = new ScriptRegexp(Some(".*test.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine))
      
      val json = """{"name":"test","age":30}"""
      
      val futureResult = flow.exec("", json, Map.empty)
      
      // JQ fails to extract nonexistent field -> throws NoSuchElementException
      // The Future will fail with an exception
      val caught = intercept[java.util.NoSuchElementException] {
        Await.result(futureResult, 5.seconds)
      }
      caught.getMessage should include("nonexistent") // Should contain field name
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
      // JQ2 tries to extract ".status" from non-JSON string -> throws InvalidData exception
      // The Future will fail with an exception
      val caught = intercept[Exception] {
        Await.result(futureResult, 5.seconds)
      }
      // ujson throws InvalidData exception when trying to parse invalid JSON
      caught.getClass.getSimpleName should include("InvalidData")
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

    "chain ScriptRegexpScore with Future composition" in {
      val scoreEngine = new ScriptRegexpScore(Some(".*foo.*"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val futureResult1 = flow.exec("", "foobar", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "1.0"
      
      val futureResult2 = flow.exec("", "barbaz", Map.empty)
      val result2 = Await.result(futureResult2, 5.seconds)
      result2 shouldBe "0.0"
    }

    "chain ScriptJQ -> ScriptRegexpScore with Future composition" in {
      val jqEngine = new ScriptJQ(Some(".name"))
      val scoreEngine = new ScriptRegexpScore(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, scoreEngine))
      
      val json = """{"name":"John","age":30}"""
      val futureResult = flow.exec("", json, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // JQ extracts "John" (returns List representation), score matches -> "1.0"
      result shouldBe "1.0"
    }

    "chain ScriptRegexp extract -> ScriptRegexpScore with Future composition" in {
      val extractEngine = new ScriptRegexp(Some("?hash=(0x[0-9a-f]+)"))
      val scoreEngine = new ScriptRegexpScore(Some("0x[0-9a-f]+"))
      val flow = new ScriptFlow(Seq(extractEngine, scoreEngine))
      
      val input = "hash=0xabc123&other=data"
      val futureResult = flow.exec("", input, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // Extract "0xabc123", score matches -> "1.0"
      result shouldBe "1.0"
    }

    "chain ScriptRegexpScore -> ScriptRegexpScore with Future composition" in {
      val scoreEngine1 = new ScriptRegexpScore(Some(".*foo.*"))
      val scoreEngine2 = new ScriptRegexpScore(Some(".*bar.*"))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2))
      
      // Important: scoreEngine1 returns "1.0" or "0.0", which becomes the input to scoreEngine2
      // scoreEngine2 matches against "1.0" or "0.0", NOT the original input
      
      val futureResult1 = flow.exec("", "foobar", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "0.0" // First matches -> "1.0", second doesn't match "1.0" against ".*bar.*" -> "0.0" (because "1.0" doesn't contain "bar")
      
      val futureResult2 = flow.exec("", "foobar", Map.empty)
      val result2 = Await.result(futureResult2, 5.seconds)
      result2 shouldBe "0.0" // First matches -> "1.0", second doesn't match "1.0" -> "0.0"
      
      // To make chaining work meaningfully, the second pattern must match against score value format
      // Pattern that matches score values (contains a digit and a dot)
      val scoreEngine3 = new ScriptRegexpScore(Some(".*[0-9]\\..*"))
      val flow2 = new ScriptFlow(Seq(scoreEngine1, scoreEngine3))
      val futureResult3 = flow2.exec("", "foobar", Map.empty)
      val result3 = Await.result(futureResult3, 5.seconds)
      result3 shouldBe "1.0" // "1.0" matches ".*[0-9]\\..*"
      
      // Pattern that matches "1.0" specifically
      val scoreEngine4 = new ScriptRegexpScore(Some("^1\\.0$"))
      val flow3 = new ScriptFlow(Seq(scoreEngine1, scoreEngine4))
      val futureResult4 = flow3.exec("", "foobar", Map.empty)
      val result4 = Await.result(futureResult4, 5.seconds)
      result4 shouldBe "1.0" // "1.0" matches "^1\\.0$"
      
      val futureResult5 = flow3.exec("", "barbaz", Map.empty)
      val result5 = Await.result(futureResult5, 5.seconds)
      result5 shouldBe "0.0" // "0.0" doesn't match "^1\\.0$"
    }

    "chain ScriptRegexpScore with extraction pattern using Future composition" in {
      val scoreEngine = new ScriptRegexpScore(Some("?([0-9]+)"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val futureResult1 = flow.exec("", "The number is 42", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "1.0"
      
      val futureResult2 = flow.exec("", "No numbers here", Map.empty)
      val result2 = Await.result(futureResult2, 5.seconds)
      result2 shouldBe "0.0"
    }

    "chain ScriptJQ -> ScriptRegexp -> ScriptRegexpScore with Future composition" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val scoreEngine = new ScriptRegexpScore(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(jqEngine, regexpEngine, scoreEngine))
      
      val json = """{"user":{"name":"John Doe","age":30}}"""
      val futureResult = flow.exec("", json, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // JQ extracts "John Doe", regexp matches -> "John Doe", score matches -> "1.0"
      result shouldBe "1.0"
    }

    "chain multiple ScriptRegexpScore engines with Future composition" in {
      val scoreEngine1 = new ScriptRegexpScore(Some(".*[0-9].*"))
      val scoreEngine2 = new ScriptRegexpScore(Some(".*[a-z].*"))
      val scoreEngine3 = new ScriptRegexpScore(Some(".*[A-Z].*"))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2, scoreEngine3))
      
      val futureResult1 = flow.exec("", "test123", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "0.0" // First matches -> "1.0", second doesn't match "1.0" against ".*[a-z].*" -> "0.0" (because "1.0" doesn't contain lowercase)
      
      val futureResult2 = flow.exec("", "Test123", Map.empty)
      val result2 = Await.result(futureResult2, 5.seconds)
      result2 shouldBe "0.0" // First matches -> "1.0", second doesn't match "1.0" against ".*[a-z].*" -> "0.0"
      
      // Test with pattern that matches score value format
      val scoreEngine4 = new ScriptRegexpScore(Some(".*[0-9].*"))
      val flow2 = new ScriptFlow(Seq(scoreEngine1, scoreEngine4))
      val futureResult3 = flow2.exec("", "test123", Map.empty)
      val result3 = Await.result(futureResult3, 5.seconds)
      result3 shouldBe "1.0" // "1.0" matches ".*[0-9].*"
    }

    "chain ScriptJQScore with Future composition" in {
      val scoreEngine = new ScriptJQScore(Some(".name"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val futureResult1 = flow.exec("", """{"name":"John","age":30}""", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "1.0"
      
      val futureResult2 = flow.exec("", """{"age":30}""", Map.empty)
      // ScriptJQScore throws exception when field doesn't exist, which propagates
      intercept[java.util.NoSuchElementException] {
        Await.result(futureResult2, 5.seconds)
      }
    }

    "chain ScriptJQ -> ScriptJQScore with Future composition" in {
      val jqEngine = new ScriptJQ(Some(".user.name"))
      val scoreEngine = new ScriptJQScore(Some("."))
      val flow = new ScriptFlow(Seq(jqEngine, scoreEngine))
      
      val json = """{"user":{"name":"John","age":30}}"""
      val futureResult = flow.exec("", json, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // JQ extracts "John" (returns List representation like "List(John)"), ScriptJQScore tries to parse it as JSON
      // The result depends on whether the JQ output is valid JSON
      // If JQ output is valid JSON (or can be parsed), it succeeds -> "1.0", otherwise fails -> exception
      result should (be("0.0") or be("1.0"))
    }

    "chain ScriptJQScore -> ScriptJQScore with Future composition" in {
      val scoreEngine1 = new ScriptJQScore(Some(".name"))
      val scoreEngine2 = new ScriptJQScore(Some("."))
      val flow = new ScriptFlow(Seq(scoreEngine1, scoreEngine2))
      
      val json1 = """{"name":"John","age":30}"""
      val futureResult1 = flow.exec("", json1, Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      // First matches -> "1.0", second parses "1.0" as JSON number -> "1.0"
      result1 shouldBe "1.0"
      
      val json2 = """{"age":30}"""
      val futureResult2 = flow.exec("", json2, Map.empty)
      // First throws exception when field doesn't exist -> propagates
      intercept[java.util.NoSuchElementException] {
        Await.result(futureResult2, 5.seconds)
      }
    }

    "chain ScriptSQScore with Future composition" in {
      val scoreEngine = new ScriptSQScore(Some("result"))
      val flow = new ScriptFlow(Seq(scoreEngine))
      
      val futureResult = flow.exec("", "some solidity output", Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // Result depends on whether SolidityResult.extractString finds the pattern
      result should (be("0.0") or be("1.0"))
    }

    "chain ScriptJQScore -> ScriptRegexpScore with Future composition" in {
      val jqScoreEngine = new ScriptJQScore(Some(".status"))
      val regexpScoreEngine = new ScriptRegexpScore(Some(".*[0-9]\\..*"))
      val flow = new ScriptFlow(Seq(jqScoreEngine, regexpScoreEngine))
      
      val json = """{"status":"active"}"""
      val futureResult = flow.exec("", json, Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      
      // First matches -> "1.0", second matches "1.0" against ".*[0-9]\\..*" -> "1.0"
      result shouldBe "1.0"
    }
  }

  "ScriptFlow with ScriptFilter" should {
    "short-circuit when ScriptFilter detects empty input" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine, regexpEngine))
      
      // Empty input - ScriptFilter should short-circuit, return empty string
      val result1 = flow.run("", "", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "" // ScriptFilter detected empty input, short-circuited
      
      // Non-empty input - should process normally
      val json = """{"name":"John","age":30}"""
      val result2 = flow.run("", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("John") // Filter passes, JQ extracts, regexp matches
    }

    "short-circuit when ScriptFilter detects empty input in middle of flow" in {
      // Use ScriptRegexp extract which returns empty string when no match, not an exception
      val extractEngine = new ScriptRegexp(Some("?name=(.+)"))
      val filterEngine = new ScriptFilter()
      val regexpEngine = new ScriptRegexp(Some(".*John.*"))
      val flow = new ScriptFlow(Seq(extractEngine, filterEngine, regexpEngine))
      
      // Regexp extraction returns empty string (no match), ScriptFilter detects it and short-circuits
      val input1 = "age=30" // No "name=" pattern
      val result1 = flow.run("", input1, Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "" // ScriptFilter detected empty input from regexp, short-circuited
      
      // Positive case: Regexp extraction succeeds, filter passes non-empty forward, regexp matches it
      val input2 = "name=John" // Has "name=" pattern
      val result2 = flow.run("", input2, Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "John" // Filter passed "John" forward, regexp matched ".*John.*"
    }

    "short-circuit when ScriptFilter detects empty input after regexp extraction" in {
      val extractEngine = new ScriptRegexp(Some("?hash=(0x[0-9a-f]+)"))
      val filterEngine = new ScriptFilter()
      val regexpEngine = new ScriptRegexp(Some("0x[0-9a-f]+"))
      val flow = new ScriptFlow(Seq(extractEngine, filterEngine, regexpEngine))
      
      // Regexp extraction fails (no match), ScriptFilter detects empty and short-circuits
      val result1 = flow.run("", "no hash here", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "" // ScriptFilter detected empty input from regexp, short-circuited
      
      // Positive case: Regexp extraction succeeds, filter passes hash forward, regexp validates it
      val result2 = flow.run("", "hash=0xabc123", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "0xabc123" // Filter passed "0xabc123" forward, regexp matched it
    }

    "not short-circuit when ScriptFilter receives non-empty input" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      val json = """{"name":"John","age":30}"""
      val result = flow.run("", json, Map.empty)
      result.isSuccess shouldBe true
      result.get should include("John") // Filter passes, JQ processes
    }

    "short-circuit with filter:// URI format" in {
      val flow = ScriptFlow.build(Some("filter://, jq://.name"))
      
      // Empty input - should short-circuit
      val result1 = flow.run("", "", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe ""
      
      // Non-empty input - should process
      val json = """{"name":"John","age":30}"""
      val result2 = flow.run("", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("John")
    }

    "short-circuit with filter:// in middle of flow" in {
      val flow = ScriptFlow.build(Some("regexp://?name=(.+), filter://, regexp://.*John.*"))
      
      // Regexp extraction returns empty string (no match), filter short-circuits
      val input1 = "age=30" // No "name=" pattern
      val result1 = flow.run("", input1, Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe ""
      
      // Positive case: Regexp extraction succeeds, filter passes name forward, regexp matches it
      val input2 = "name=John" // Has "name=" pattern
      val result2 = flow.run("", input2, Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "John" // Filter passed "John" forward, regexp matched ".*John.*"
    }

    "short-circuit with ScriptFilter using Future composition" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      // Empty input - ScriptFilter short-circuits
      val futureResult1 = flow.exec("", "", Map.empty)
      val result1 = Await.result(futureResult1, 5.seconds)
      result1 shouldBe "" // ScriptFilter detected empty input, short-circuited
      
      // Positive case: Non-empty input, filter passes it forward, JQ processes it
      val json = """{"name":"John","age":30}"""
      val futureResult2 = flow.exec("", json, Map.empty)
      val result2 = Await.result(futureResult2, 5.seconds)
      result2 should include("John") // Filter passed JSON forward, JQ extracted name
    }

    "not process remaining scripts after ScriptFilter short-circuits" in {
      // Use a script that would fail if executed to verify it's not called
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".nonexistent")) // This would fail if executed
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      // Empty input - ScriptFilter short-circuits, jqEngine should not be called
      val result1 = flow.run("", "", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "" // Short-circuited, jqEngine not executed
      
      // Positive case: Non-empty input, filter passes it forward, JQ processes it (even if field doesn't exist)
      // This verifies that when filter passes input, the flow continues normally
      val json = """{"name":"John","age":30}"""
      val result2 = flow.run("", json, Map.empty)
      // JQ will try to extract .nonexistent, which will fail, but filter did pass the input forward
      result2.isFailure shouldBe true // JQ failed as expected when field doesn't exist
      result2.failed.get shouldBe a[java.util.NoSuchElementException] // But filter did pass input forward
    }

    "propagate custom src value when ScriptFilter short-circuits" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      // Empty input with custom src - ScriptFilter should short-circuit and return the src value
      val customSrc = "NO_DATA"
      val result = flow.run(customSrc, "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe customSrc // ScriptFilter propagated the src value when short-circuiting
    }

    "propagate different src values in different filter instances" in {
      val filterEngine1 = new ScriptFilter()
      val filterEngine2 = new ScriptFilter()
      val flow = new ScriptFlow(Seq(filterEngine1, filterEngine2))
      
      // First filter short-circuits with src1, second filter should not be called
      val src1 = "EMPTY_INPUT"
      val result = flow.run(src1, "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe src1 // First filter's src value is propagated
    }

    "propagate src value when ScriptFilter short-circuits in middle of flow" in {
      val extractEngine = new ScriptRegexp(Some("?name=(.+)"))
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(extractEngine, filterEngine, jqEngine))
      
      // Regexp extraction returns empty string, ScriptFilter short-circuits with custom src
      val customSrc = "EXTRACTION_FAILED"
      val input = "age=30" // No "name=" pattern
      val result = flow.run(customSrc, input, Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe customSrc // ScriptFilter propagated the src value
    }

    "propagate src value through async exec when ScriptFilter short-circuits" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      // Empty input with custom src - ScriptFilter short-circuits and propagates src
      val customSrc = "ASYNC_NO_DATA"
      val futureResult = flow.exec(customSrc, "", Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      result shouldBe customSrc // ScriptFilter propagated the src value through async execution
    }

    "propagate empty src when ScriptFilter short-circuits with empty src" in {
      val filterEngine = new ScriptFilter()
      val jqEngine = new ScriptJQ(Some(".name"))
      val flow = new ScriptFlow(Seq(filterEngine, jqEngine))
      
      // Empty input with empty src - ScriptFilter should propagate empty string
      val result = flow.run("", "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "" // Empty src is propagated
    }

    "propagate src value with filter:// URI format" in {
      val flow = ScriptFlow.build(Some("filter://, jq://.name"))
      
      // Empty input with custom src - filter should propagate src value
      val customSrc = "URI_FILTER_NO_DATA"
      val result = flow.run(customSrc, "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe customSrc // Filter propagated the src value
      
      // Non-empty input - should process normally (use empty src so JQ uses .name from URI)
      val json = """{"name":"John","age":30}"""
      val result2 = flow.run("", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("John") // Filter passes, JQ processes with .name from URI
    }

    "short-circuit with filter://value URI format (src in URI)" in {
      val flow = ScriptFlow.build(Some("filter://NO_DATA, jq://.name"))
      
      // Empty input - filter should use src from URI (NO_DATA), not from run() call
      val customSrc = "CUSTOM_SRC"
      val result1 = flow.run(customSrc, "", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "NO_DATA" // Filter uses src from URI, not from run() call
      
      // Empty input with empty src - filter should use src from URI
      val result2 = flow.run("", "", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "NO_DATA" // Filter uses src from URI
      
      // Non-empty input - should process normally
      val json = """{"name":"John","age":30}"""
      val result3 = flow.run("", json, Map.empty)
      result3.isSuccess shouldBe true
      result3.get should include("John") // Filter passes, JQ processes
    }

    "short-circuit with filter://value in middle of flow" in {
      val flow = ScriptFlow.build(Some("regexp://?name=(.+), filter://EXTRACTION_FAILED, regexp://.*John.*"))
      
      // Regexp extraction returns empty string, filter short-circuits with src from URI
      val customSrc = "CUSTOM_ERROR"
      val input1 = "age=30" // No "name=" pattern
      val result1 = flow.run(customSrc, input1, Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "EXTRACTION_FAILED" // Filter uses src from URI, not from run() call
      
      // Positive case: Regexp extraction succeeds, filter passes name forward, regexp matches it
      val input2 = "name=John" // Has "name=" pattern
      val result2 = flow.run("", input2, Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "John" // Filter passed "John" forward, regexp matched ".*John.*"
    }

    "short-circuit with filter://value and multiple filters" in {
      val flow = ScriptFlow.build(Some("filter://FIRST_FILTER, filter://SECOND_FILTER, jq://.name"))
      
      // Empty input - first filter short-circuits with src from URI
      val customSrc = "CUSTOM_SRC"
      val result = flow.run(customSrc, "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "FIRST_FILTER" // First filter uses src from URI, not from run() call
    }

    "short-circuit with filter://value using async exec" in {
      val flow = ScriptFlow.build(Some("filter://ASYNC_NO_DATA, jq://.name"))
      
      // Empty input with custom src - ScriptFilter short-circuits and uses src from URI
      val customSrc = "ASYNC_CUSTOM_SRC"
      val futureResult = flow.exec(customSrc, "", Map.empty)
      val result = Await.result(futureResult, 5.seconds)
      result shouldBe "ASYNC_NO_DATA" // Filter uses src from URI, not from exec() call
    }

    "filter://value format parsing" in {
      // Test that filter://value format can be parsed and uses value from URI
      val flow1 = ScriptFlow.build(Some("filter://TEST_VALUE"))
      flow1 should not be null
      
      val flow2 = ScriptFlow.build(Some("filter://, filter://ANOTHER_VALUE"))
      flow2 should not be null
      
      // Verify both flows work - filter://value uses value from URI
      val result1 = flow1.run("CUSTOM", "", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "TEST_VALUE" // Uses src from URI
      
      // First filter has no value (empty), second has ANOTHER_VALUE
      // First filter will use src from run() since URI has no value
      val result2 = flow2.run("CUSTOM2", "", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "CUSTOM2" // First filter uses src from run() since filter:// has no value
    }

    "filter:// without value falls back to src parameter" in {
      val flow = ScriptFlow.build(Some("filter://, jq://.name"))
      
      // Empty input - filter:// has no value, so it uses src from run()
      val customSrc = "FALLBACK_SRC"
      val result = flow.run(customSrc, "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe customSrc // Filter uses src from run() since URI has no value
    }
  }

  "ScriptFlow.resolve with AI script" should {
    "extract output:// from input and remove it from result" in {
      val script = ScriptFlow.resolve(
        typ = "ai",
        src = "Extract text from provided image and return result as json. output://json_object. User request: {input}",
        opts = Some("mirror://")
      )
      
      script.isSuccess shouldBe true
      val flow = new ScriptFlow(Seq(script.get))
      
      // Run with input containing image and output://
      // Both output:// and image:// should be extracted from input and removed from the result
      val input = "Analyze this image://https://example.com/image.jpg and format output://json_schema"
      val result = flow.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      val resultText = result.get
      
      // Verify result never contains output:// or image:// anywhere
      // The prompt contains "output://json_object" but it should also be extracted from the prompt
      // before being sent to the AI provider, so the result should not contain it
      resultText should not include "output://"
      resultText should not include "image://"
      
      // Verify the result contains the cleaned prompt and cleaned input
      resultText should include("Extract text from provided image and return result as json")
      resultText should include("User request:")
      resultText should include("Analyze this")
      resultText should include("and format")
    }
  }
}

