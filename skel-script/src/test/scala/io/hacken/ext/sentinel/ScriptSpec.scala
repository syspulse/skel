package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

// This file contains tests for ScriptBuilder and Script factory methods.
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

  "ScriptBuilder" should {
    "build ScriptRegexp from builder" in {
      val script = ScriptRegexp.build(Some(".*foo.*"))
      script.getId() shouldBe "regexp"
      script.run("", "foobar", Map.empty) shouldBe Success("foobar")
    }

    "build ScriptJQ from builder" in {
      val script = ScriptJQ.build(Some(".name"))
      script.getId() shouldBe "sq" // Note: ScriptJQ.build returns ScriptSQ
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
  }

  "Script.apply" should {
    "find and build ScriptRegexp by ID" in {
      val result = Script("regexp", Some(".*foo.*"))
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "regexp"
      result.get.run("", "foobar", Map.empty) shouldBe Success("foobar")
    }

    "find and build ScriptJQ by ID" in {
      val result = Script("jq", Some(".name"))
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "sq" // Note: ScriptJQ.build returns ScriptSQ
    }

    "find and build ScriptJS by ID" in {
      val result = Script("js", None)
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "js"
    }

    "find and build ScriptJS by ID with src0" in {
      val result = Script("js", Some("input.length"))
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "js"
      // Test that src0 is used when run("") is called
      val runResult = result.get.run("", "hello", Map.empty)
      runResult.isSuccess shouldBe true
      runResult.get shouldBe "5"
    }

    "find and build ScriptAI by ID" in {
      val result = Script("ai", Some("openrouter://model"))
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "ai"
    }

    "find and build ScriptStr by ID" in {
      val result = Script("str", None)
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "str"
    }

    "find and build ScriptNone by empty ID" in {
      val result = Script("", None)
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "none"
    }

    "return Failure for unknown script ID" in {
      val result = Script("unknown", None)
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("Script not found")
    }

    "handle whitespace in script ID" in {
      val result = Script("  regexp  ", Some(".*foo.*"))
      result.isSuccess shouldBe true
      result.get.getId() shouldBe "regexp"
    }
  }

  "Script.find" should {
    "find ScriptRegexp builder" in {
      val builder = Script.find("regexp")
      builder.isDefined shouldBe true
      builder.get.build(Some(".*foo.*")).getId() shouldBe "regexp"
    }

    "find ScriptJQ builder" in {
      val builder = Script.find("jq")
      builder.isDefined shouldBe true
    }

    "find ScriptJS builder" in {
      val builder = Script.find("js")
      builder.isDefined shouldBe true
    }

    "find ScriptAI builder" in {
      val builder = Script.find("ai")
      builder.isDefined shouldBe true
    }

    "return None for unknown script ID" in {
      val builder = Script.find("unknown")
      builder.isEmpty shouldBe true
    }
  }

  "Script.add" should {
    "add custom script builder and find it" in {
      val customBuilder = new ScriptBuilder {
        def build(src: Option[String]): Script = new ScriptStr()
      }
      
      Script.add("custom", customBuilder)
      val builder = Script.find("custom")
      builder.isDefined shouldBe true
      
      val script = Script("custom", None)
      script.isSuccess shouldBe true
      script.get.getId() shouldBe "str"
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
