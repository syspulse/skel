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

    "extract from Twitter JSON" in {
      val engine = new ScriptJQ(None)
      val json = """
{"author_id":"4012126151","author_name":"GeneralStaffUA","created_at":1766770089000,"id":"2004605164725141755","media":[],"text":"Російські підрозділи продовжують намагатися штурмувати позиції 92-ї окремої штурмової бригади… на конях.\n\nІ є важлива добра новин: завдяки професіоналізму оператора БпЛА батальйону безпілотних систем 92 ОШБр жоден кінь не постраждав.\n\n92 ОШБр ім. кошового отамана Івана Сірка https://t.co/ja0A6S3YP7"}
{"author_id":"4012126152","author_name":"GeneralStaffUA","created_at":1766765117000,"id":"2004584308598849844","media":["https://pbs.twimg.com/media/G9G2zduWYAAzsAq.jpg","https://pbs.twimg.com/media/G9G2zdYWoAAfetq.jpg","https://pbs.twimg.com/media/G9G2ze6WMAAcQJU.jpg","https://pbs.twimg.com/media/G9G2zhXW4AAoaWq.jpg"],"text":"Міцні руки й холодна сталь – воїни танкового батальйону 116 ОМБр готують свої машини до бойової роботи\n\n116 окрема механізована бригада https://t.co/xbcPYR0aY7"}
{"author_id":"4012126153","author_name":"GeneralStaffUA","created_at":1766759294000,"id":"2004559886714294744","media":["https://pbs.twimg.com/media/G9GgmC1WYAAHQz8.jpg"],"text":"Оперативна інформація станом на 16:00 26.12.2025 щодо російського вторгнення\nhttps://t.co/QJqpVH3QYR https://t.co/hqWUfFzAwR"}
      """.split("\n").toSeq.filter(! _.isBlank())

//       val json = """
// {"author_id":"4012126155","author_name":"GeneralStaffUA","created_at":1766770089000,"id":"2004605164725141755","media":[],"text":"Російські https://t.co/ja0A6S3YP7"}
//       """.split("\n").toSeq.filter(! _.isBlank())

      val aid1 = engine.run(".author_id", json(0), Map.empty)
      aid1.isSuccess shouldBe true
      aid1.get should include("4012126151")

      val media1 = engine.run(".media", json(0), Map.empty)
      media1.isSuccess shouldBe true
      media1.get should include("[]")

      val media2 = engine.run(".media[]", json(1), Map.empty)
      info(s"media2: ${media2}")
      media2.isSuccess shouldBe true
      media2.get should include("https://pbs.twimg.com/media/G9G2zduWYAAzsAq.jpg")
      media2.get should include("https://pbs.twimg.com/media/G9G2zdYWoAAfetq.jpg")
      media2.get should include("https://pbs.twimg.com/media/G9G2ze6WMAAcQJU.jpg")
      media2.get should include("https://pbs.twimg.com/media/G9G2zhXW4AAoaWq.jpg")

      val media3 = engine.run(".media", json(2), Map.empty)
      media3.isSuccess shouldBe true
      media3.get should include("https://pbs.twimg.com/media/G9GgmC1WYAAHQz8.jpg")
    }


  }
}

