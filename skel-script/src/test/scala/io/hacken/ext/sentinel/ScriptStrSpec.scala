package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success

class ScriptStrSpec extends AnyWordSpec with Matchers {

  "ScriptStr" should {
    "build from builder" in {
      val script = ScriptStr.build(None)
      script.getId() shouldBe "str"
      script.name shouldBe "string"
    }

    "pass through input when src0 is None (default {input})" in {
      val script = ScriptStr.build(None)
      script.run("", "hello", Map.empty) shouldBe Success("hello")
      script.run("", "", Map.empty) shouldBe Success("")
    }

    "replace {input} in src0 with input" in {
      val script = ScriptStr.build(Some("prefix {input} suffix"))
      script.run("", "world", Map.empty) shouldBe Success("prefix world suffix")
    }

    "replace multiple {input} occurrences in src0" in {
      val script = ScriptStr.build(Some("{input} and {input}"))
      script.run("", "x", Map.empty) shouldBe Success("x and x")
    }

    "return literal src0 when it has no {input} placeholder" in {
      val script = ScriptStr.build(Some("static template"))
      script.run("", "ignored", Map.empty) shouldBe Success("static template")
    }

    "handle empty input with {input} placeholder" in {
      val script = ScriptStr.build(Some("value=[{input}]"))
      script.run("", "", Map.empty) shouldBe Success("value=[]")
    }

    "ignore run() src parameter and use src0 only" in {
      val script = ScriptStr.build(Some("from src0: {input}"))
      script.run("from run: {input}", "data", Map.empty) shouldBe Success("from src0: data")
    }

    "resolve via ScriptFlow.resolve with str type" in {
      val script = ScriptFlow.resolve("str", "Hello {input}!", None)
      script.isSuccess shouldBe true
      script.get.run("", "world", Map.empty) shouldBe Success("Hello world!")
    }
  }
}
