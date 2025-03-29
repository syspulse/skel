package io.syspulse.skel.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.duration._

class CacheExpireSpec extends AnyWordSpec with Matchers {
  
  case class TestData(id: String, value: String)
  
  class TestCache(expiry: Long) extends CacheExpire[String, TestData, String](expiry) {
    override def key(v: TestData): String = v.id
    override def index(v: TestData): String = v.value
  }

  "CacheExpire" should {
    "store and retrieve values" in {
      val cache = new TestCache(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.get("1") shouldBe Some(data)
    }

    "expire values after timeout" in {
      val cache = new TestCache(100)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.get("1") shouldBe Some(data)
      
      Thread.sleep(200)
      cache.get("1") shouldBe None
    }

    "return default value when key not found" in {
      val cache = new TestCache(1000)
      val default = TestData("default", "default")
      
      cache.getOrElse("nonexistent", default) shouldBe Some(default)
    }

    "update existing values" in {
      val cache = new TestCache(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.upsert("1", data, d => d.copy(value = "updated")) shouldBe Some(TestData("1", "updated"))
    }

    "find by index" in {
      val cache = new TestCache(1000)
      val data = TestData("1", "test-index")
      
      cache.put(data)
      cache.findByIndex("test-index") shouldBe Some(data)
    }

    "clean expired entries" in {
      val cache = new TestCache(100)
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
      val cache = new TestCache(1000)
      val data = TestData("1", "test")
      
      cache.put(data)
      cache.refresh(data, fresh = 2) shouldBe Some(data)
      cache.refresh(data, fresh = 2) shouldBe Some(data)
      cache.refresh(data, fresh = 2) shouldBe None // Exceeds fresh count
    }
  }
} 