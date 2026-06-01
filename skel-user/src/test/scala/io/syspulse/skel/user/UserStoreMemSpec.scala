package io.syspulse.skel.user

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.jvm.uuid._

import io.syspulse.skel.user.server._
import io.syspulse.skel.user.store._

class UserStoreMemSpec extends AnyWordSpec with Matchers {

  "UserStoreMem" should {
    "create user with only required email" in {
      val store = new UserStoreMem()
      val user = UserRegistry.userFromCreateReq(UUID.random, UserCreateReq(email = "a@b.com"))
      store.+(user).isSuccess shouldBe true
      store.findByEmail("a@b.com").get.name shouldBe None
    }

    "store and merge meta on update" in {
      val store = new UserStoreMem()
      val id = UUID.random
      store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "meta@b.com",
        meta = Some(Map("role" -> "user")),
      ))).get

      val updated = store.update(id, UserUpdateReq(meta = Some(Map("role" -> "admin", "n" -> 1)))).get
      updated.meta shouldBe Some(Map("role" -> "admin", "n" -> 1))
      updated.email shouldBe "meta@b.com"
    }

    "apply partial update without clearing omitted fields" in {
      val store = new UserStoreMem()
      val id = UUID.random
      store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "p@b.com",
        name = Some("Pat"),
        xid = Some("xid-1"),
      ))).get

      val updated = store.update(id, UserUpdateReq(name = Some("Patricia"))).get
      updated.name shouldBe Some("Patricia")
      updated.xid shouldBe Some("xid-1")
    }

    "find by xid case-insensitively" in {
      val store = new UserStoreMem()
      val id = UUID.random
      store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "x@b.com",
        xid = Some("0xAbC"),
      ))).get

      store.findByXid("0xabc").map(_.id) shouldBe Some(id)
    }

    "page users with from and size" in {
      val store = new UserStoreMem()
      (1 to 5).foreach { i =>
        store.+(User(UUID.random, s"page-$i@example.com")).get
      }

      store.??(1, 2).size shouldBe 2
      store.??(3, 2).size shouldBe 2
      store.??(10, 2) shouldBe empty
      store.??(-1, 1).size shouldBe 1
      store.??(0, 0) shouldBe empty
      store.all.size shouldBe 5
    }
  }
}
