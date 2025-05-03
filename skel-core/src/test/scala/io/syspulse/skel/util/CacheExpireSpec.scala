package io.syspulse.skel.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class CacheExpireSpec extends AnyWordSpec with Matchers {
  
  case class TestData(id: String, value: String)
  
  class TestCacheIndex(expiry: Long) extends CacheIndexExpire[String, TestData, String](expiry) {
    override def key(v: TestData): String = v.id
    override def index(v: TestData): String = v.value
  }

  "CacheIndexExpire" should {
    "store and retrieve values" in {
      val cache = new TestCacheIndex(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.get("1") shouldBe Some(data)
    }

    "expire values after timeout" in {
      val cache = new TestCacheIndex(100)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.get("1") shouldBe Some(data)
      
      Thread.sleep(350)
      cache.get("1") shouldBe None
    }

    "return default value when key not found" in {
      val cache = new TestCacheIndex(1000)
      val default = TestData("default", "default")
      
      cache.getOrElse("nonexistent", default) shouldBe Some(default)
    }

    "update existing values" in {
      val cache = new TestCacheIndex(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.upsert("1", data, d => d.copy(value = "updated")) shouldBe Some(TestData("1", "updated"))
    }

    "find by index" in {
      val cache = new TestCacheIndex(1000)
      val data = TestData("1", "test-index")
      
      cache.put(data)
      cache.findByIndex("test-index") shouldBe Some(data)
    }

    "clean expired entries" in {
      val cache = new TestCacheIndex(100)
      val data1 = TestData("1", "test1")
      val data2 = TestData("2", "test2")
      
      cache.put(data1)
      Thread.sleep(200)
      cache.put(data2)
      
      cache.size shouldBe 1
      cache.clean()
      cache.size shouldBe 1
      cache.get("1") shouldBe None
      cache.get("2") shouldBe Some(data2)
    }

    "refresh entries" in {
      val cache = new TestCacheIndex(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.refresh(data, fresh = 2) shouldBe Some(data)
      cache.refresh(data, fresh = 2) shouldBe Some(data)
      cache.refresh(data, fresh = 2) shouldBe None // Exceeds fresh count
    }
  }

  "CacheExpire" should {
    "create cache with default expiry" in {
      val cache = CacheExpire[String, String](1000)
      cache.put("key1", "value1")
      cache.get("key1") shouldBe Some("value1")
    }

    "expire values after timeout" in {
      val cache = CacheExpire[String, String](100)
      cache.put("key1", "value1")
      cache.get("key1") shouldBe Some("value1")
      
      Thread.sleep(200)
      cache.get("key1") shouldBe None
    }

    "handle multiple values with different expiry times" in {
      val cache = CacheExpire[String, String](500)
      cache.put("key1", "value1")
      Thread.sleep(200)
      cache.put("key2", "value2")
      
      cache.get("key1") shouldBe Some("value1")
      cache.get("key2") shouldBe Some("value2")
      
      Thread.sleep(400)
      cache.get("key1") shouldBe None
      cache.get("key2") shouldBe Some("value2")
    }

    "clean expired entries" in {
      val cache = CacheExpire[String, String](200)
      cache.put("key1", "value1")
      cache.put("key2", "value2")      
      Thread.sleep(100)
      cache.put("key3", "value3")
      
      cache.size shouldBe 3
      
      Thread.sleep(100)
      cache.clean()
      cache.size shouldBe 1

      cache.get("key1") shouldBe None
      cache.get("key2") shouldBe None
      cache.get("key3") shouldBe Some("value3")
    }
    
    // "handle concurrent access" in {
    //   val cache = CacheExpire[String, String](1000)
    //   val threads = (1 to 10).map { i =>
    //     new Thread(() => {
    //       cache.put(s"key$i", s"value$i")
    //       Thread.sleep(10)
    //       cache.get(s"key$i")
    //     })
    //   }
      
    //   threads.foreach(_.start())
    //   threads.foreach(_.join())
      
    //   cache.size shouldBe 10
    //   (1 to 10).foreach { i =>
    //     cache.get(s"key$i") shouldBe Some(s"value$i")
    //   }
    // }
  }
} 