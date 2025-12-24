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
      script.run("", "foobar", Map.empty) shouldBe Success("true")
    }

    "build ScriptJQ from builder" in {
      val script = ScriptJQ.build(Some(".name"))
      script.getId() shouldBe "sq" // Note: ScriptJQ.build returns ScriptSQ
    }

    "build ScriptJS from builder" in {
      val script = ScriptJS.build(None)
      script.getId() shouldBe "js"
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
      result.get.run("", "foobar", Map.empty) shouldBe Success("true")
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
}
