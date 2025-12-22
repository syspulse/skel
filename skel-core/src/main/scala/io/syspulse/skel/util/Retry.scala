package io.syspulse.skel.util

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Retry {
  def withRetry[T](operation: => T, desc: String)(timeout: Long = 10000, retry: Int = 3, baseWait: Long = 3000)(implicit log: Logger): Try[T] = {
    Try{ withRetrying(operation,desc)(timeout,retry,baseWait)(log) }
  }

  def withRetrying[T](operation: => T, desc: String)(timeout: Long = 10000, retry: Int = 3, baseWait: Long = 3000)(implicit log: Logger): T = {
    def err(e: Exception, i: Int, r: Option[String] = None): T = {
      if (i > 1) {
        val waitTime = baseWait * math.pow(2, retry - i).toLong
        log.warn(s"Request failed: ${desc}: ${i}: ${e}: body='${r.getOrElse("")}': retrying in ${waitTime}")
        Thread.sleep(waitTime)
        retryWithBackoff(i - 1)
      } else {
        log.error(s"Request failed after: ${retry}: ${desc}: ${r.getOrElse("")}", e)
        throw e
      }
    }

    def retryWithBackoff(i: Int): T = {
      try {
        operation
      } catch {
        case e: requests.RequestFailedException =>
          err(e, i, Some(e.response.text()))
        case e: Exception =>
          err(e, i)
      }
    }
    retryWithBackoff(retry)
  }

  def withRetryFuture[T](operation: => Future[T], desc: String)(retry: Int = 3, baseWait: Long = 3000)(implicit log: Logger, ec: ExecutionContext): Future[T] = {
    def retryWithBackoff(remaining: Int): Future[T] = {
      operation.recoverWith {
        case e: Exception if remaining > 1 =>
          val waitTime = baseWait * math.pow(2, retry - remaining).toLong
          log.warn(s"Request failed: ${desc}: ${remaining}: ${e.getMessage}: retrying in ${waitTime}ms")
          Thread.sleep(waitTime)
          retryWithBackoff(remaining - 1)
        case e: Exception =>
          log.error(s"Request failed after ${retry} retries: ${desc}: ${e.getMessage}", e)
          Future.failed(e)
      }
    }
    retryWithBackoff(retry)
  }
}
