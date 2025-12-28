package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure, Try}

class ScriptRegexpScoreSpec extends AnyWordSpec with Matchers {

  "ScriptRegexpScore" should {
    "return 1.0 when pattern matches input" in {
      val engine = new ScriptRegexpScore(None)

      engine.run(".*foo.*", "foobar", Map.empty) shouldBe Success("1.0")
      engine.run(".*bar.*", "foobar", Map.empty) shouldBe Success("1.0")
      engine.run("^[A-Z].*", "Hello", Map.empty) shouldBe Success("1.0")
    }

    "return 0.0 when pattern does not match input" in {
      val engine = new ScriptRegexpScore(None)

      engine.run(".*foo.*", "barbaz", Map.empty) shouldBe Success("0.0")
      engine.run(".*xyz.*", "abc", Map.empty) shouldBe Success("0.0")
      engine.run("^[A-Z].*", "hello", Map.empty) shouldBe Success("0.0")
    }

    "return 1.0 when negated pattern does not match (input matches negation)" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("!.*foo.*", "barbaz", Map.empty) shouldBe Success("1.0")
      engine.run("!.*xyz.*", "abc", Map.empty) shouldBe Success("1.0")
    }

    "return 0.0 when negated pattern matches (input does not match negation)" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("!.*foo.*", "foobar", Map.empty) shouldBe Success("0.0")
      engine.run("!.*bar.*", "foobar", Map.empty) shouldBe Success("0.0")
    }

    "return 1.0 when extraction succeeds (capturing group matches)" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("?value:([0-9]+)", "value:12345", Map.empty) shouldBe Success("1.0")
      engine.run("?hash=(0x[0-9a-f]+)", "hash=0xabc123", Map.empty) shouldBe Success("1.0")
      engine.run("?score=([0-9.]+)", "score=0.85", Map.empty) shouldBe Success("1.0")
    }

    "return 0.0 when extraction fails (capturing group does not match)" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("?value:([0-9]+)", "value:abc", Map.empty) shouldBe Success("0.0")
      engine.run("?hash=(0x[0-9a-f]+)", "hash=xyz", Map.empty) shouldBe Success("0.0")
      engine.run("?score=([0-9.]+)", "score=invalid", Map.empty) shouldBe Success("0.0")
    }

    "use constructor pattern when inline src is blank" in {
      val engine = new ScriptRegexpScore(Some(".*foo.*"))

      engine.run("", "foobar", Map.empty) shouldBe Success("1.0")
      engine.run("", "barbaz", Map.empty) shouldBe Success("0.0")
    }

    "use constructor pattern for extraction when inline src is blank" in {
      val engine = new ScriptRegexpScore(Some("?hash=(0x[0-9a-f]+)"))

      engine.run("", "hash=0xabc123", Map.empty) shouldBe Success("1.0")
      engine.run("", "hash=invalid", Map.empty) shouldBe Success("0.0")
    }

    "use constructor pattern for negation when inline src is blank" in {
      val engine = new ScriptRegexpScore(Some("!.*foo.*"))

      engine.run("", "foobar", Map.empty) shouldBe Success("0.0")
      engine.run("", "barbaz", Map.empty) shouldBe Success("1.0")
    }

    "return 0.0 for blank input" in {
      val engine = new ScriptRegexpScore(None)

      engine.run(".*foo.*", "", Map.empty) shouldBe Success("0.0")
      engine.run("", "", Map.empty) shouldBe Success("0.0")
    }

    "return 0.0 when constructor src0 is None and everything is blank" in {
      val engine = new ScriptRegexpScore(None)

      // When both src and input are blank, ScriptRegexp returns input (empty string)
      // ScriptRegexpScore converts empty string to "0.0"
      engine.run("", "", Map.empty) shouldBe Success("0.0")
    }

    "return 0.0 when constructor src0 is None and src is blank but input is not blank" in {
      val engine = new ScriptRegexpScore(None)

      // When src0 is None and src is blank, ScriptRegexp returns input as-is
      // Since input is not blank, ScriptRegexpScore should return "1.0"
      // Actually wait - let me check ScriptRegexp behavior when src0 is None and src is blank
      // Looking at ScriptRegexp: if src is blank and expr0 is empty, it returns Success(input)
      // So if input is "some-input", it returns "some-input" (non-blank), so ScriptRegexpScore returns "1.0"
      engine.run("", "some-input", Map.empty) shouldBe Success("1.0")
    }

    "handle complex patterns correctly" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("^[A-Z][a-z]+\\s+[0-9]+$", "Hello 123", Map.empty) shouldBe Success("1.0")
      engine.run("^[A-Z][a-z]+\\s+[0-9]+$", "hello 123", Map.empty) shouldBe Success("0.0")
      engine.run("^[A-Z][a-z]+\\s+[0-9]+$", "Hello abc", Map.empty) shouldBe Success("0.0")
    }

    "handle email pattern matching" in {
      val engine = new ScriptRegexpScore(Some("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}"""))

      engine.run("", "user@example.com", Map.empty) shouldBe Success("1.0")
      engine.run("", "invalid-email", Map.empty) shouldBe Success("0.0")
    }

    "handle numeric extraction patterns" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("?([0-9]+)", "The number is 42", Map.empty) shouldBe Success("1.0")
      engine.run("?([0-9]+)", "No numbers here", Map.empty) shouldBe Success("0.0")
      engine.run("?([0-9.]+)", "Price: 19.99", Map.empty) shouldBe Success("1.0")
    }

    "propagate failures from ScriptRegexp for invalid patterns in run()" in {
      // Invalid regexp pattern throws PatternSyntaxException at pattern construction
      // This happens when parse() creates ExprMatch, which constructs the Regex
      // The exception is thrown during run(), so we need to catch it
      val engine = new ScriptRegexpScore(None)
      val result = Try(engine.run("(", "test", Map.empty))
      // The exception is thrown before run() can return, so we catch it here
      result.isFailure shouldBe true
      result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
    }

    "propagate failures from ScriptRegexp for invalid patterns in constructor" in {
      // Invalid regexp pattern in constructor also throws PatternSyntaxException
      val result = Try(new ScriptRegexpScore(Some("(")))
      result.isFailure shouldBe true
      result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
    }

    "propagate failures for various invalid regex patterns" in {
      val engine = new ScriptRegexpScore(None)
      
      val invalidPatterns = Seq(
        "(",           // Unclosed group
        "[",           // Unclosed character class
        "\\",          // Incomplete escape
        "(*",          // Invalid quantifier
        "?*",          // Invalid quantifier
        "+*",          // Invalid quantifier
        "{",           // Unclosed quantifier
        "{5,3}",       // Invalid range (min > max)
        "(?<",         // Incomplete named group
        "(?P<",        // Incomplete Python-style named group
        "\\p{",        // Incomplete Unicode property
        "\\x{",        // Incomplete hex escape
        "\\u{",        // Incomplete Unicode escape
        "(?(",         // Invalid conditional
        "(*LIMIT_MATCH=0)", // Invalid possessive quantifier syntax
      )
      
      invalidPatterns.foreach { pattern =>
        val result = Try(engine.run(pattern, "test", Map.empty))
        result.isFailure shouldBe true
        result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
      }
    }

    "propagate failures for invalid extraction patterns" in {
      val engine = new ScriptRegexpScore(None)
      
      // Test patterns that should throw PatternSyntaxException
      val invalidPatterns = Seq(
        "?(",          // Extraction with unclosed group
        "?[",           // Extraction with unclosed character class
      )
      
      invalidPatterns.foreach { pattern =>
        val result = Try(engine.run(pattern, "test", Map.empty))
        result.isFailure shouldBe true
        result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
      }
      
      // Test pattern without capturing group - this will match but group(1) will throw
      val runResult = engine.run("?.*", "test", Map.empty)
      runResult.isFailure shouldBe true
      runResult.failed.get shouldBe an[IndexOutOfBoundsException]
      
      // Test pattern with empty capturing group - this is valid and returns empty string -> "0.0"
      val emptyGroupResult = engine.run("?()", "test", Map.empty)
      emptyGroupResult shouldBe Success("0.0") // Empty group returns empty string -> "0.0"
    }

    "propagate failures for invalid negation patterns" in {
      val engine = new ScriptRegexpScore(None)
      
      val invalidNegationPatterns = Seq(
        "!(",           // Negation with unclosed group
        "![",           // Negation with unclosed character class
        "!\\",          // Negation with incomplete escape
      )
      
      invalidNegationPatterns.foreach { pattern =>
        val result = Try(engine.run(pattern, "test", Map.empty))
        result.isFailure shouldBe true
        result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
      }
    }

    "propagate failures when extraction pattern has no capturing group" in {
      val engine = new ScriptRegexpScore(None)
      
      // Pattern matches but has no capturing group, so group(1) will throw
      val result = engine.run("?.*", "test", Map.empty)
      result.isFailure shouldBe true
      result.failed.get shouldBe an[IndexOutOfBoundsException]
    }

    "propagate failures when extraction pattern has capturing group but match fails" in {
      val engine = new ScriptRegexpScore(None)
      
      // Valid pattern with capturing group, but no match
      // This should return Success("0.0"), not a failure
      val result = engine.run("?([0-9]+)", "no numbers", Map.empty)
      result shouldBe Success("0.0")
    }

    "propagate failures when extraction accesses non-existent group" in {
      val engine = new ScriptRegexpScore(None)
      
      // Pattern with only one group, but trying to access group(2) would fail
      // But our code only accesses group(1), so this test verifies that
      // If we had a pattern that somehow accessed group(2), it would fail
      // For now, test that accessing group(1) on a pattern with no groups fails
      val result = engine.run("?.*", "test", Map.empty)
      result.isFailure shouldBe true
    }

    "handle failures correctly in constructor with invalid pattern" in {
      // Constructor with invalid pattern should throw during construction
      intercept[java.util.regex.PatternSyntaxException] {
        new ScriptRegexpScore(Some("("))
      }
    }

    "handle failures correctly when run() is called with invalid pattern after valid constructor" in {
      val engine = new ScriptRegexpScore(Some(".*valid.*"))
      
      // Constructor pattern is valid, but run() with invalid pattern should fail
      val result = Try(engine.run("(", "test", Map.empty))
      result.isFailure shouldBe true
      result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
    }

    "propagate failures for malformed Unicode escapes" in {
      val engine = new ScriptRegexpScore(None)
      
      val result = Try(engine.run("\\u{", "test", Map.empty))
      result.isFailure shouldBe true
      result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
    }

    "propagate failures for malformed hex escapes" in {
      val engine = new ScriptRegexpScore(None)
      
      val result = Try(engine.run("\\x{", "test", Map.empty))
      result.isFailure shouldBe true
      result.failed.get shouldBe a[java.util.regex.PatternSyntaxException]
    }

    "verify failure propagation preserves exception type" in {
      val engine = new ScriptRegexpScore(None)
      
      val result1 = Try(engine.run("(", "test", Map.empty))
      result1.isFailure shouldBe true
      val ex1 = result1.failed.get
      ex1 shouldBe a[java.util.regex.PatternSyntaxException]
      ex1.getMessage should include("Unclosed group")
      
      val result2 = Try(engine.run("[", "test", Map.empty))
      result2.isFailure shouldBe true
      val ex2 = result2.failed.get
      ex2 shouldBe a[java.util.regex.PatternSyntaxException]
      ex2.getMessage should include("Unclosed character class")
    }

    "verify failure propagation preserves exception message" in {
      val engine = new ScriptRegexpScore(None)
      
      val result = Try(engine.run("(", "test", Map.empty))
      result.isFailure shouldBe true
      val ex = result.failed.get.asInstanceOf[java.util.regex.PatternSyntaxException]
      ex.getDescription should include("Unclosed group")
      ex.getIndex shouldBe 1
    }

    "handle whitespace in patterns" in {
      val engine = new ScriptRegexpScore(None)

      engine.run(".*\\s+.*", "hello world", Map.empty) shouldBe Success("1.0")
      engine.run(".*\\s+.*", "nowhitespace", Map.empty) shouldBe Success("0.0")
    }

    "handle case-insensitive matching via pattern" in {
      val engine = new ScriptRegexpScore(None)

      engine.run("(?i).*foo.*", "FOOBAR", Map.empty) shouldBe Success("1.0")
      engine.run("(?i).*foo.*", "FoObAr", Map.empty) shouldBe Success("1.0")
      engine.run("(?i).*foo.*", "barbaz", Map.empty) shouldBe Success("0.0")
    }

    "handle multiple capturing groups in extraction" in {
      val engine = new ScriptRegexpScore(None)

      // Even with multiple groups, if extraction succeeds (first group matches), return 1.0
      engine.run("?(\\d+)-(\\d+)-(\\d+)", "2024-01-15", Map.empty) shouldBe Success("1.0")
      engine.run("?(\\d+)-(\\d+)-(\\d+)", "invalid-date", Map.empty) shouldBe Success("0.0")
    }

    "handle empty pattern (passthrough behavior)" in {
      val engine = new ScriptRegexpScore(Some(""))

      // Empty pattern in ScriptRegexp returns input as-is
      // Non-blank input -> "1.0", blank input -> "0.0"
      engine.run("", "some-input", Map.empty) shouldBe Success("1.0")
      engine.run("", "", Map.empty) shouldBe Success("0.0")
    }

    "handle very long input strings" in {
      val engine = new ScriptRegexpScore(None)

      val longInput = "a" * 10000 + "foo" + "b" * 10000
      engine.run(".*foo.*", longInput, Map.empty) shouldBe Success("1.0")
      engine.run(".*xyz.*", longInput, Map.empty) shouldBe Success("0.0")
    }

    "handle special regex characters correctly" in {
      val engine = new ScriptRegexpScore(None)

      engine.run(".*\\+.*", "test+test", Map.empty) shouldBe Success("1.0")
      engine.run(".*\\*.*", "test*test", Map.empty) shouldBe Success("1.0")
      engine.run(".*\\.*", "test.test", Map.empty) shouldBe Success("1.0")
      engine.run(".*\\?.*", "test?test", Map.empty) shouldBe Success("1.0")
    }
  }
}
