package io.syspulse.skel.util

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

object Retry {  
  
  def withRetry[T](operation: => T, desc: String)(timeout: Long = 10000, retry: Int = 3, baseWait: Long = 1000)(implicit log: Logger): Try[T] = {
    Try{ withRetrying(operation,desc)(timeout,retry,baseWait)(log) }
  }

  def withRetrying[T](operation: => T, desc: String)(timeout: Long = 10000, retry: Int = 3, baseWait: Long = 1000)(implicit log: Logger): T = {
    def retryWithBackoff(i: Int): T = {
      try {
        operation
      } catch {
        case e: Exception =>
          if (i > 1) {
            val waitTime = baseWait * math.pow(2, retry - i).toLong
            log.warn(s"Request failed: ${desc}: ${i}: retrying in ${waitTime}: ${e.getMessage}")
            Thread.sleep(waitTime)
            retryWithBackoff(i - 1)
          } else {
            log.error(s"failed after: ${retry}: ${desc}", e)
            // throw awau
            throw e
          }
      }
    }
    retryWithBackoff(retry)
  }

}
