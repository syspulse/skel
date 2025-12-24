package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Success

class ScriptRegexpSpec extends AnyWordSpec with Matchers {

  "ScriptRegexp" should {
    "return true when inline pattern matches input" in {
      val engine = new ScriptRegexp(None)

      engine.run(".*foo.*", "foobar", Map.empty) shouldBe Success("true")
    }

    "return false when inline pattern does not match input" in {
      val engine = new ScriptRegexp(None)

      engine.run(".*foo.*", "barbaz", Map.empty) shouldBe Success("false")
    }

    "support negated inline patterns prefixed with '!'" in {
      val engine = new ScriptRegexp(None)

      engine.run("!.*foo.*", "barbaz", Map.empty) shouldBe Success("true")
      engine.run("!.*foo.*", "foobar", Map.empty) shouldBe Success("false")
    }
    
    "extract value when inline regexp has capturing group" in {
      val engine = new ScriptRegexp(None)

      engine.run("?value:([0-9]+)", "value:12345", Map.empty) shouldBe Success("12345")
    }

    "use constructor pattern when inline src is blank" in {
      val engine = new ScriptRegexp(Some(".*foo.*"))

      engine.run("", "foobar", Map.empty) shouldBe Success("true")
      engine.run("", "barbaz", Map.empty) shouldBe Success("false")
    }
    
    "extract value when constructor regexp has capturing group" in {
      val engine = new ScriptRegexp(Some("?hash=(0x[0-9a-f]+)"))

      engine.run("", "hash=0xabc123", Map.empty) shouldBe Success("0xabc123")
    }

    "use default constructor src0 == None as passthrough when everything blank" in {
      val engine = new ScriptRegexp(None)

      engine.run("", "some-input", Map.empty) shouldBe Success("some-input")
    }

    "ignore blank input" in {
      val engine = new ScriptRegexp(None)

      engine.run(".*foo.*", "", Map.empty) shouldBe Success("")
    }

    "honour negated constructor pattern" in {
      val engine = new ScriptRegexp(Some("!.*foo.*"))
      
      engine.run("", "foobar", Map.empty) shouldBe Success("false")
      engine.run("", "barbaz", Map.empty) shouldBe Success("true")
    }
  }
}

