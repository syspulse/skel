package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}

class ScriptConditionSpec extends AnyWordSpec with Matchers {

  "ScriptCondition" should {
    "build from builder" in {
      val script = ScriptCondition.build(Some("> 10"))
      script.getId() shouldBe "condition"
      script.name shouldBe "condition"
    }

    "build with None uses empty condition" in {
      val script = ScriptCondition.build(None)
      script.getId() shouldBe "condition"
      // Empty condition always returns false in set()
      script.run("", "42", Map.empty).isFailure shouldBe true
    }

    "return Failure(ScriptBreakException) for blank input" in {
      val script = ScriptCondition.build(Some("> 0"))
      val result = script.run("", "", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "> 0"
    }

    "pass when condition \"> 10\" is satisfied" in {
      val script = ScriptCondition.build(Some("> 10"))
      script.run("", "15", Map.empty) shouldBe Success("15")
      script.run("", "11", Map.empty) shouldBe Success("11")
    }

    "fail when condition \"> 10\" is not satisfied" in {
      val script = ScriptCondition.build(Some("> 10"))
      val result = script.run("", "5", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
      result.failed.get.asInstanceOf[Script.ScriptBreakException].src shouldBe "> 10"
    }

    "pass when condition \"< 5\" is satisfied" in {
      val script = ScriptCondition.build(Some("< 5"))
      script.run("", "3", Map.empty) shouldBe Success("3")
      script.run("", "-1", Map.empty) shouldBe Success("-1")
    }

    "fail when condition \"< 5\" is not satisfied" in {
      val script = ScriptCondition.build(Some("< 5"))
      val result = script.run("", "10", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
    }

    "pass when condition \"= 42\" is satisfied" in {
      val script = ScriptCondition.build(Some("= 42"))
      script.run("", "42", Map.empty) shouldBe Success("42")
    }

    "fail when condition \"= 42\" is not satisfied" in {
      val script = ScriptCondition.build(Some("= 42"))
      val result = script.run("", "41", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe a[Script.ScriptBreakException]
    }

    "pass when condition \">= 100\" is satisfied" in {
      val script = ScriptCondition.build(Some(">= 100"))
      script.run("", "100", Map.empty) shouldBe Success("100")
      script.run("", "150", Map.empty) shouldBe Success("150")
    }

    "fail when condition \">= 100\" is not satisfied" in {
      val script = ScriptCondition.build(Some(">= 100"))
      val result = script.run("", "99", Map.empty)
      result.isFailure shouldBe true
    }

    "empty condition always fails" in {
      val script = ScriptCondition.build(Some(""))
      script.run("", "1", Map.empty).isFailure shouldBe true
      script.run("", "99", Map.empty).isFailure shouldBe true
    }
  }
}
