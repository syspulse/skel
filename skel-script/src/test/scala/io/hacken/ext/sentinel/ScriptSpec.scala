package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

// This file contains tests for Script object build methods.
// Other tests have been split into separate spec files:
// - ScriptRegexpSpec.scala
// - ScriptJQSpec.scala
// - ScriptAISpec.scala
// - ScriptFlowSpec.scala
// - ScriptScoreSpec.scala
// - ScriptFlowConcurrencySpec.scala
//
// Run individual specs for faster test execution:
//   sbt "project skel_script" "testOnly io.syspulse.skel.script.ScriptRegexpSpec"
//   sbt "project skel_script" "testOnly io.syspulse.skel.script.ScriptJQSpec"
//   etc.

class ScriptSpec extends AnyWordSpec with Matchers {

  "Script object build methods" should {
    "build ScriptRegexp from builder" in {
      val script = ScriptRegexp.build(Some(".*foo.*"))
      script.getId() shouldBe "regexp"
      script.run("", "foobar", Map.empty) shouldBe Success("foobar")
    }

    "build ScriptJQ from builder" in {
      val script = ScriptJQ.build(Some(".name"))
      script.getId() shouldBe "jq"
    }

    "build ScriptJS from builder" in {
      val script = ScriptJS.build(None)
      script.getId() shouldBe "js"
    }

    "build ScriptJS from builder with src0" in {
      val script = ScriptJS.build(Some("input.toUpperCase()"))
      script.getId() shouldBe "js"
      // Test that src0 is used when run("") is called
      val result = script.run("", "hello", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "HELLO"
    }

    "build ScriptAI from builder" in {
      val script = ScriptAI.build(Some("openrouter://model"))
      script.getId() shouldBe "ai"
    }

    "build ScriptStr from builder" in {
      val script = ScriptStr.build(None)
      script.getId() shouldBe "str"
    }

    "build ScriptNone from builder" in {
      val script = ScriptNone.build(None)
      script.getId() shouldBe "none"
    }

    "build ScriptRegexpScore from builder" in {
      val script = ScriptRegexpScore.build(Some(".*foo.*"))
      script.getId() shouldBe "regexp_score"
      script.run("", "foobar", Map.empty) shouldBe Success("1.0")
      script.run("", "barbaz", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptRegexpScore from builder with extraction pattern" in {
      val script = ScriptRegexpScore.build(Some("?([0-9]+)"))
      script.getId() shouldBe "regexp_score"
      script.run("", "The number is 42", Map.empty) shouldBe Success("1.0")
      script.run("", "No numbers here", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptRegexpScore from builder with negation pattern" in {
      val script = ScriptRegexpScore.build(Some("!.*foo.*"))
      script.getId() shouldBe "regexp_score"
      script.run("", "barbaz", Map.empty) shouldBe Success("1.0")
      script.run("", "foobar", Map.empty) shouldBe Success("0.0")
    }

    "build ScriptJQScore from builder" in {
      val script = ScriptJQScore.build(Some(".name"))
      script.getId() shouldBe "jq_score"
      script.run("", """{"name":"John","age":30}""", Map.empty) shouldBe Success("1.0")
    }

    "build ScriptJQScore from builder (negative - field not found)" in {
      val script = ScriptJQScore.build(Some(".nonexistent"))
      script.getId() shouldBe "jq_score"
      val runResult = script.run("", """{"name":"John"}""", Map.empty)
      runResult.isFailure shouldBe true
      runResult.failed.get shouldBe a[java.util.NoSuchElementException]
    }

    "build ScriptSQScore from builder" in {
      val script = ScriptSQScore.build(Some("result"))
      script.getId() shouldBe "sq_score"
      script.name shouldBe "solidity-query-score"
    }

    "build ScriptSQScore from builder (empty pattern)" in {
      val script = ScriptSQScore.build(Some(""))
      script.getId() shouldBe "sq_score"
      script.name shouldBe "solidity-query-score"
    }
  }

  "ScriptJS with src0" should {
    "use src0 script when run(\"\") is called with empty src" in {
      val script = new ScriptJS(src0 = Some("input.toUpperCase()"))
      val result1 = script.run("", "hello", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "HELLO"
      
      val result2 = script.run("", "world", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "WORLD"
    }

    "use src parameter when provided, ignoring src0" in {
      val script = new ScriptJS(src0 = Some("input.toUpperCase()"))
      // When src is provided, it should be used instead of src0
      val result = script.run("input.toLowerCase()", "HELLO", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "hello"
    }

    "handle different input types with src0" in {
      val script = new ScriptJS(src0 = Some("input * 2"))
      val result1 = script.run("", "5", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "10"
      
      val script2 = new ScriptJS(src0 = Some("input + '_suffix'"))
      val result2 = script2.run("", "test", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "test_suffix"
    }

    "handle complex transformations with src0" in {
      val script = new ScriptJS(src0 = Some("input.split('').reverse().join('')"))
      val result = script.run("", "hello", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "olleh"
    }

    "handle numeric operations with src0" in {
      val script = new ScriptJS(src0 = Some("parseInt(input) + 10"))
      val result1 = script.run("", "5", Map.empty)
      result1.isSuccess shouldBe true
      result1.get shouldBe "15"
      
      val result2 = script.run("", "20", Map.empty)
      result2.isSuccess shouldBe true
      result2.get shouldBe "30"
    }

    "handle string concatenation with src0" in {
      val script = new ScriptJS(src0 = Some("'prefix_' + input + '_suffix'"))
      val result = script.run("", "middle", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "prefix_middle_suffix"
    }

    "work with empty input and src0" in {
      val script = new ScriptJS(src0 = Some("input || 'default'"))
      val result = script.run("", "", Map.empty)
      result.isSuccess shouldBe true
      result.get shouldBe "default"
    }
  }
}
