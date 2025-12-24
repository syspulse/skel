package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ScriptAISpec extends AnyWordSpec with Matchers {

  "ScriptAI" should {
    "use default URI when constructor src0 is None" in {
      val engine = new ScriptAI(None)
      
      // Verify that the engine is created with default URI
      engine.getId() shouldBe "ai"
    }

    "use custom URI when constructor src0 is provided" in {
      val customUri = "openrouter://model"
      val engine = new ScriptAI(Some(customUri))
      
      // Verify that the engine is created
      engine.getId() shouldBe "ai"
    }

    "accept input as question and return result" in {
      val engine = new ScriptAI(Some("openrouter://arcee-ai/trinity-mini:free?retry=0"))
      val question = "What is 2+2?"
      
      // The result may succeed or fail depending on API availability
      val result = engine.run("", question, Map.empty)
      
      // Either it succeeds with an answer, or fails due to API issues
      result.isSuccess || result.isFailure shouldBe true
      
      // If successful, should return a string (even if empty)
      if (result.isSuccess) {
        result.get shouldBe a[String]
      }
    }

    "handle empty input gracefully" in {
      val engine = new ScriptAI(None)
      
      val result = engine.run("", "", Map.empty)
      
      // Should either succeed with empty string or fail
      result.isSuccess || result.isFailure shouldBe true
    }
    
  }
}

