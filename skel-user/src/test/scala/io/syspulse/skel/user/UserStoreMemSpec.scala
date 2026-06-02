package io.syspulse.skel.user

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import io.jvm.uuid._

import io.syspulse.skel.user.server._
import io.syspulse.skel.user.store._

class UserStoreMemSpec extends AnyWordSpec with Matchers {

  val timeout = Duration(5, "seconds")

  "UserStoreMem" should {
    "create user with only required email" in {
      val store = new UserStoreMem()
      val user = UserRegistry.userFromCreateReq(UUID.random, UserCreateReq(email = "a@b.com"))
      Await.result(store.+(user), timeout)
      val found = Await.result(store.findByEmail("a@b.com"), timeout)
      found.get.name shouldBe None
    }

    "store and merge meta on update" in {
      val store = new UserStoreMem()
      val id = UUID.random
      Await.result(store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "meta@b.com",
        meta = Some(Map("role" -> "user")),
      ))), timeout)

      val updated = Await.result(store.update(id, UserUpdateReq(meta = Some(Map("role" -> "admin", "n" -> 1)))), timeout)
      updated.meta shouldBe Some(Map("role" -> "admin", "n" -> 1))
      updated.email shouldBe "meta@b.com"
    }

    "apply partial update without clearing omitted fields" in {
      val store = new UserStoreMem()
      val id = UUID.random
      Await.result(store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "p@b.com",
        name = Some("Pat"),
        xid = Some("xid-1"),
      ))), timeout)

      val updated = Await.result(store.update(id, UserUpdateReq(name = Some("Patricia"))), timeout)
      updated.name shouldBe Some("Patricia")
      updated.xid shouldBe Some("xid-1")
    }

    "find by xid case-insensitively" in {
      val store = new UserStoreMem()
      val id = UUID.random
      Await.result(store.+(UserRegistry.userFromCreateReq(id, UserCreateReq(
        email = "x@b.com",
        xid = Some("0xAbC"),
      ))), timeout)

      val found = Await.result(store.findByXid("0xabc"), timeout)
      found.map(_.id) shouldBe Some(id)
    }

    "page users with from and size" in {
      val store = new UserStoreMem()
      (1 to 5).foreach { i =>
        Await.result(store.+(User(UUID.random, s"page-$i@example.com")), timeout)
      }

      Await.result(store.???(1, 2), timeout).size shouldBe 2
      Await.result(store.???(3, 2), timeout).size shouldBe 2
      Await.result(store.???( 10, 2), timeout) shouldBe empty
      Await.result(store.???(-1, 1), timeout).size shouldBe 1
      Await.result(store.???(0, 0), timeout) shouldBe empty
      Await.result(store.all, timeout).size shouldBe 5
    }

    "search users case-insensitively with minimum query length" in {
      val store = new UserStoreMem()
      val id1 = UUID.random
      val id2 = UUID.random
      val id3 = UUID.random

      Await.result(store.+(User(id1, "alpha.search@example.com", name = Some("Needle Name"))), timeout)
      Await.result(store.+(User(id2, "beta@example.com", xid = Some("wallet-needle-xid"))), timeout)
      Await.result(store.+(User(id3, "gamma@example.com", name = Some("Other"))), timeout)

      Await.result(store.search("NEEDLE"), timeout).map(_.id).toSet shouldBe Set(id1, id2)
      Await.result(store.search("'needle'"), timeout).map(_.id).toSet shouldBe Set(id1, id2)
      Await.result(store.search("ne"), timeout) shouldBe empty
    }

    "page search results with from and size" in {
      val store = new UserStoreMem()
      (1 to 5).foreach { i =>
        Await.result(store.+(User(UUID.random, s"pager-$i@example.com", name = Some(s"pager-name-$i"))), timeout)
      }

      Await.result(store.search("pager", Some(0), Some(2)), timeout).size shouldBe 2
      Await.result(store.search("pager", Some(2), Some(2)), timeout).size shouldBe 2
      Await.result(store.search("pager", Some(10), Some(2)), timeout) shouldBe empty
    }
  }
}
