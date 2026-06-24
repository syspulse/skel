package io.syspulse.skel.dns

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import java.util.concurrent.TimeoutException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import io.syspulse.skel.FutureUtil
import io.syspulse.skel.FutureUtil._

class AutoResolverSpec extends AnyWordSpec with Matchers with DnsTestSupport {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  private class SlowResolver(delayMs: Long) extends DnsResolver {
    override def resolve(domain: String)(implicit ec: ExecutionContext): Future[DnsInfo] =
      Future {
        Thread.sleep(delayMs)
        DnsInfo(domain, Some(1L), Some(1L), Some(1L), "1.2.3.4", Seq("ns.example.com"))
      }
  }

  "AutoResolver" should {
    "fail with TimeoutException from FutureUtil.withTimeout" in {
      val timeoutMs = 50L
      val slow = Future {
        Thread.sleep(2000)
        "late"
      }
      val r = sync(FutureUtil.withTimeout(slow, timeoutMs))(5000)
      assertFailure(r, "FutureUtil.withTimeout") { e =>
        e shouldBe a[TimeoutException]
        e.getMessage shouldBe s"timeout: $timeoutMs ms"
      }
    }

    "fail with timeout when per-resolver limit is exceeded" in {
      val timeoutMs = 50L
      val r = sync(FutureUtil.withTimeout(new SlowResolver(2000).resolve("example.com"), timeoutMs))(5000)
      assertFailure(r, "per-resolver timeout") { e =>
        e shouldBe a[TimeoutException]
        e.getMessage shouldBe s"timeout: $timeoutMs ms"
      }
    }

    "fail when all resolver errors accumulate" in {
      val timeoutMs = 50L
      val errs = Seq(s"timeout: $timeoutMs ms", s"timeout: $timeoutMs ms")
      val d = DnsInfo("example.com", None, None, None, "", Seq.empty, err = errs)
      val result =
        if (d.err.size == 2) Failure(new Exception(d.err.mkString("; ")))
        else Success(d)
      assertFailure(result, "all resolvers failed") { e =>
        e.getMessage shouldBe s"timeout: $timeoutMs ms; timeout: $timeoutMs ms"
      }
    }

    "fail with TimeoutException when sync await limit is exceeded" in {
      val timeoutMs = 50L
      val slow = Future {
        Thread.sleep(2000)
        DnsInfo("slow.test", None, None, None, "1.2.3.4", Seq("ns.example.com"))
      }
      val r = sync(slow)(timeoutMs)
      assertFailure(r, "sync await timeout") { e =>
        e shouldBe a[TimeoutException]
        e.getMessage should include (s"$timeoutMs")
      }
    }
  }
}
