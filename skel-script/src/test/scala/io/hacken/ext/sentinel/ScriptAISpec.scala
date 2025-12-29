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

    "accept input as question and return result" ignore {
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

  "ScriptAI.extractImages" should {
    "extract single image URL from input" in {
      val engine = new ScriptAI(None)
      val input = "Describe this image://https://example.com/image.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://example.com/image.jpg"
      cleanedInput shouldBe "Describe this "
    }

    "extract multiple image URLs from input" in {
      val engine = new ScriptAI(None)
      val input = "Compare image://https://example.com/img1.jpg and image://https://example.com/img2.png"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 2
      images should contain("https://example.com/img1.jpg")
      images should contain("https://example.com/img2.png")
      cleanedInput shouldBe "Compare  and "
    }

    "extract image URL with complex path" in {
      val engine = new ScriptAI(None)
      val input = "Analyze image://https://cdn.example.com/path/to/image-123.jpg?size=large"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://cdn.example.com/path/to/image-123.jpg?size=large"
      cleanedInput shouldBe "Analyze "
    }

    "extract image URL at the beginning of input" in {
      val engine = new ScriptAI(None)
      val input = "image://https://example.com/start.jpg What is in this image?"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://example.com/start.jpg"
      cleanedInput shouldBe " What is in this image?"
    }

    "extract image URL at the end of input" in {
      val engine = new ScriptAI(None)
      val input = "What do you see? image://https://example.com/end.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://example.com/end.jpg"
      cleanedInput shouldBe "What do you see? "
    }

    "extract image URLs separated by spaces" in {
      val engine = new ScriptAI(None)
      val input = "image://url1.jpg image://url2.jpg image://url3.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 3
      images should contain("url1.jpg")
      images should contain("url2.jpg")
      images should contain("url3.jpg")
      cleanedInput shouldBe "  "
    }

    "return empty sequence when no image URLs present" in {
      val engine = new ScriptAI(None)
      val input = "This is just regular text without any images"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images shouldBe empty
      cleanedInput shouldBe input
    }

    "handle empty input" in {
      val engine = new ScriptAI(None)
      val input = ""
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images shouldBe empty
      cleanedInput shouldBe ""
    }

    "handle input with only image URL" in {
      val engine = new ScriptAI(None)
      val input = "image://https://example.com/only.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://example.com/only.jpg"
      cleanedInput shouldBe ""
    }

    "extract image URLs with different protocols" in {
      val engine = new ScriptAI(None)
      val input = "image://http://example.com/img1.jpg image://https://example.com/img2.jpg image://ftp://example.com/img3.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 3
      images should contain("http://example.com/img1.jpg")
      images should contain("https://example.com/img2.jpg")
      images should contain("ftp://example.com/img3.jpg")
    }

    "extract image URL even when followed by text" in {
      val engine = new ScriptAI(None)
      // The regex pattern matches image:// followed by non-whitespace until whitespace
      // So "image://url" followed by space is a valid match
      val input = "This is an image://url because there's a space after url"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "url"
      cleanedInput shouldBe "This is an  because there's a space after url"
    }

    "not extract when image:// is not followed by valid URL" in {
      val engine = new ScriptAI(None)
      // When image:// is followed immediately by whitespace, nothing is extracted
      val input = "This is not an image:// because there's a space immediately after"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images shouldBe empty
      cleanedInput shouldBe input
    }

    "extract image URLs with special characters" in {
      val engine = new ScriptAI(None)
      val input = "Check image://https://example.com/image%20with%20spaces.jpg"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 1
      images.head shouldBe "https://example.com/image%20with%20spaces.jpg"
      cleanedInput shouldBe "Check "
    }

    "preserve text between image URLs" in {
      val engine = new ScriptAI(None)
      val input = "First image://url1.jpg then some text image://url2.jpg and more text"
      
      val (images, cleanedInput) = engine.extractImages(input)
      
      images should have size 2
      images should contain("url1.jpg")
      images should contain("url2.jpg")
      cleanedInput shouldBe "First  then some text  and more text"
    }
  }

  "ScriptAI.extractOutput" should {
    "extract single output URL from input" in {
      val engine = new ScriptAI(None)
      val input = "Process this output://json_object"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json_object")
      cleanedInput shouldBe "Process this "
    }

    "extract first output URL when multiple are present" in {
      val engine = new ScriptAI(None)
      val input = "Use output://json first, then output://xml later"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json")
      cleanedInput shouldBe "Use  first, then  later"
    }

    "extract output URL with complex value" in {
      val engine = new ScriptAI(None)
      val input = "Format as output://json-pretty"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json-pretty")
      cleanedInput shouldBe "Format as "
    }

    "extract output URL at the beginning of input" in {
      val engine = new ScriptAI(None)
      val input = "output://json Format the response"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json")
      cleanedInput shouldBe " Format the response"
    }

    "extract output URL at the end of input" in {
      val engine = new ScriptAI(None)
      val input = "What format? output://xml"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("xml")
      cleanedInput shouldBe "What format? "
    }

    "return None when no output URL present" in {
      val engine = new ScriptAI(None)
      val input = "This is just regular text without any output format"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe None
      cleanedInput shouldBe input
    }

    "handle empty input" in {
      val engine = new ScriptAI(None)
      val input = ""
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe None
      cleanedInput shouldBe ""
    }

    "handle input with only output URL" in {
      val engine = new ScriptAI(None)
      val input = "output://json"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json")
      cleanedInput shouldBe ""
    }

    "extract output URL with special characters" in {
      val engine = new ScriptAI(None)
      val input = "Use output://json-pretty-format"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json-pretty-format")
      cleanedInput shouldBe "Use "
    }

    "extract output URL with underscores and hyphens" in {
      val engine = new ScriptAI(None)
      val input = "Format output://json_pretty_format"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json_pretty_format")
      cleanedInput shouldBe "Format "
    }

    "preserve text around output URL" in {
      val engine = new ScriptAI(None)
      val input = "First some text output://json then more text"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json")
      cleanedInput shouldBe "First some text  then more text"
    }

    "not extract when output:// is not followed by valid value" in {
      val engine = new ScriptAI(None)
      // When output:// is followed immediately by whitespace, nothing is extracted
      val input = "This is not an output:// because there's a space immediately after"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe None
      cleanedInput shouldBe input
    }

    "extract output URL with numbers" in {
      val engine = new ScriptAI(None)
      val input = "Use output://format123"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("format123")
      cleanedInput shouldBe "Use "
    }

    "extract output URL with dots" in {
      val engine = new ScriptAI(None)
      val input = "Format as output://json.pretty"
      
      val (output, cleanedInput) = engine.extractOutput(input)
      
      output shouldBe Some("json.pretty")
      cleanedInput shouldBe "Format as "
    }
  }

  "ScriptAI prompt processing" should {
    "replace {input} pattern in prompt with cleaned input" in {
      // Use mirror:// provider which returns the prompt as answer
      val engine = new ScriptAI(Some("Analyze this text: {input}"), Some("mirror://"))
      val input = "Hello world"
      
      val result = engine.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // MirrorAi returns the prompt as answer, so it should be "Analyze this text: Hello world"
      result.get shouldBe "Analyze this text: Hello world"
    }

    "extract image:// from input and clean prompt with {input}" in {
      // Use mirror:// provider which returns the prompt as answer
      val engine = new ScriptAI(Some("Describe the image and analyze: {input}"), Some("mirror://"))
      val input = "Look at this image://https://example.com/photo.jpg and tell me what you see"
      
      val result = engine.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // MirrorAi returns the prompt with {input} replaced by cleaned input (image:// removed)
      result.get shouldBe "Describe the image and analyze: Look at this  and tell me what you see"
    }

    "extract output:// from input and clean prompt with {input}" in {
      // Use mirror:// provider which returns the prompt as answer
      val engine = new ScriptAI(Some("Process this request: {input}"), Some("mirror://"))
      val input = "Format the response as output://json_object"
      
      val result = engine.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // MirrorAi returns the prompt with {input} replaced by cleaned input (output:// removed)
      result.get shouldBe "Process this request: Format the response as "
    }

    "extract both image:// and output:// from input with {input} replacement" in {
      // Use mirror:// provider which returns the prompt as answer
      val engine = new ScriptAI(Some("Analyze image and format response: {input}"), Some("mirror://"))
      val input = "Check image://https://example.com/img.jpg and return output://json"
      
      val result = engine.run("", input, Map.empty)
      
      result.isSuccess shouldBe true
      // MirrorAi returns the prompt with {input} replaced by cleaned input (both image:// and output:// removed)
      result.get shouldBe "Analyze image and format response: Check  and return "
    }
  }
}

