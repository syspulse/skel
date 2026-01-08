package io.syspulse.skel.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class StringUtilSpec extends AnyWordSpec with Matchers {
  
  import StringUtil._

  "String === operator" should {
    
    "compare strings case-insensitively" in {
      ("Hello" === "hello") shouldBe true
      ("Hello" === "HELLO") shouldBe true
      ("Hello" === "HeLlO") shouldBe true
      ("hello" === "HELLO") shouldBe true
    }

    "return false for different strings" in {
      ("Hello" === "World") shouldBe false
      ("Hello" === "hello world") shouldBe false
      ("abc" === "def") shouldBe false
    }

    "handle empty strings" in {
      ("" === "") shouldBe true
      ("Hello" === "") shouldBe false
      ("" === "Hello") shouldBe false
    }

    "handle strings with special characters" in {
      ("Hello World" === "hello world") shouldBe true
      ("Hello-World" === "hello-world") shouldBe true
      ("Hello_World" === "hello_world") shouldBe true
      ("Hello123" === "hello123") shouldBe true
    }

    "handle unicode characters case-insensitively" in {
      ("Café" === "CAFÉ") shouldBe true
      ("Café" === "café") shouldBe true
    }

    "distinguish from standard == operator" in {
      val s1 = "Hello"
      val s2 = "hello"
      
      // Standard == is case-sensitive
      (s1 == s2) shouldBe false
      // === is case-insensitive
      (s1 === s2) shouldBe true
    }

    "work with string variables" in {
      val str1 = "TestString"
      val str2 = "teststring"
      val str3 = "TESTSTRING"
      val str4 = "DifferentString"
      
      (str1 === str2) shouldBe true
      (str1 === str3) shouldBe true
      (str2 === str3) shouldBe true
      (str1 === str4) shouldBe false
    }
  }
}
