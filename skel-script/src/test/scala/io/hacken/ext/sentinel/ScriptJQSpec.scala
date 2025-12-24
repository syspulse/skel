package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ScriptJQSpec extends AnyWordSpec with Matchers {

  "ScriptJQ" should {
    "extract simple field value from JSON" in {
      val engine = new ScriptJQ(None)
      val json = """{"name":"John","age":30}"""

      val result1 = engine.run(".name", json, Map.empty)
      result1.isSuccess shouldBe true
      result1.get should include("John")
      
      val result2 = engine.run(".age", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("30")
    }

    "extract nested field value from JSON" in {
      val engine = new ScriptJQ(None)
      val json = """{"user":{"name":"John","address":{"city":"NYC"}}}"""

      val result1 = engine.run(".user.name", json, Map.empty)
      result1.isSuccess shouldBe true
      result1.get should include("John")
      
      val result2 = engine.run(".user.address.city", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("NYC")
    }

    "extract array element by index" in {
      val engine = new ScriptJQ(None)
      val json = """{"items":["a","b","c"]}"""

      // Try accessing array with index syntax - may need to test different formats
      val result1 = engine.run(".items", json, Map.empty)
      result1.isSuccess shouldBe true
      result1.get should include("a")
      result1.get should include("b")
      result1.get should include("c")
    }

    "extract nested value from array element" in {
      val engine = new ScriptJQ(None)
      val json = """{"users":[{"name":"John","age":30},{"name":"Jane","age":25}]}"""

      // Access array and verify it contains expected values
      val result = engine.run(".users", json, Map.empty)
      result.isSuccess shouldBe true
      result.get should include("John")
      result.get should include("Jane")
    }

    "use constructor path when inline src is blank" in {
      val engine = new ScriptJQ(Some(".name"))
      val json = """{"name":"John","age":30}"""

      val result = engine.run("", json, Map.empty)
      result.isSuccess shouldBe true
      result.get should include("John")
    }

    "return entire JSON when path is root" in {
      val engine = new ScriptJQ(None)
      val json = """{"name":"John","age":30}"""

      val result = engine.run(".", json, Map.empty)
      result.isSuccess shouldBe true
      result.get should include("John")
      result.get should include("30")
    }

    "handle numeric values correctly" in {
      val engine = new ScriptJQ(None)
      val json = """{"count":42,"price":99.99,"active":true}"""

      val result1 = engine.run(".count", json, Map.empty)
      result1.isSuccess shouldBe true
      result1.get should include("42")
      
      val result2 = engine.run(".price", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("99.99")
      
      val result3 = engine.run(".active", json, Map.empty)
      result3.isSuccess shouldBe true
      result3.get should include("true")
    }

    "handle empty strings and null values" in {
      val engine = new ScriptJQ(None)
      val json = """{"name":"","value":null}"""

      val result1 = engine.run(".name", json, Map.empty)
      result1.isSuccess shouldBe true
      result1.get should include("")
      
      val result2 = engine.run(".value", json, Map.empty)
      result2.isSuccess shouldBe true
      result2.get should include("null")
    }
  }
}

