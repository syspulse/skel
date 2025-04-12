package io.syspulse.skel.ai.provider

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import io.syspulse.skel.ai.{Ai,Chat}
import io.syspulse.skel.ai.ChatMessage

trait AiProvider {  
  val log = Logger(s"${this}")

  def getTimeout():Long = 10000L
  def getRetry():Int = 3

  protected def withRetry[T](operation: => T, desc: String)(timeout: Long = 10000, retry: Int = 3, baseWait: Long = 1000): Try[T] = {
    def retryWithBackoff(i: Int): Try[T] = {
      try {
        Success(operation)
      } catch {
        case e: Exception =>
          if (i > 1) {
            val waitTime = baseWait * math.pow(2, retry - i).toLong
            log.warn(s"Request failed: ${desc}: ${i}: retrying in ${waitTime}: ${e.getMessage}")
            Thread.sleep(waitTime)
            retryWithBackoff(i - 1)
          } else {
            log.error(s"failed after: ${retry}: ${desc}", e)
            Failure(e)
          }
      }
    }
    retryWithBackoff(retry)
  }

  // single question (no context)
  def ask(question:String,model:Option[String],system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Ai]  
  // chat (with context by Chat)
  def chat(chat:Chat,model:Option[String],system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Chat]
  // prompt (with context by Provider)
  def prompt(ai:Ai,model:Option[String],system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Ai]
}