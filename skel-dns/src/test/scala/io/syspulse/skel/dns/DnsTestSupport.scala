package io.syspulse.skel.dns

import org.scalatest.matchers.should.Matchers
import scala.util.{Failure, Success, Try}
import scala.concurrent.{ExecutionContext, Future}
import io.syspulse.skel.FutureUtil._

trait DnsTestSupport { self: Matchers =>

  implicit def ec: ExecutionContext

  def syncDns[A](f: Future[A]): Try[A] = sync(f)(DnsUtil.TIMEOUT)

  def assertSuccess[A](t: Try[A], label: String)(assertions: A => Unit): Unit =
    t match {
      case Success(v) => assertions(v)
      case Failure(e) =>
        fail(
          s"$label: expected Success, got ${e.getClass.getSimpleName}: " +
            Option(e.getMessage).filter(_.nonEmpty).getOrElse("<no message>")
        )
    }

  def assertFailure(t: Try[_], label: String)(assertions: Throwable => Unit): Unit =
    t match {
      case Failure(e) => assertions(e)
      case Success(v) => fail(s"$label: expected Failure, got Success: $v")
    }

  def assertResolvedWithIpAndNs(t: Try[DnsInfo], label: String, minNs: Int = 1): Unit =
    assertSuccess(t, label) { d =>
      d.ip should not be empty
      d.ns should not be empty
      d.ns.size should be >= minNs
    }
}
