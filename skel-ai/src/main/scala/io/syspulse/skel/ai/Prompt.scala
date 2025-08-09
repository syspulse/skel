package io.syspulse.skel.ai

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.Ingestable

case class ChatMessage(
  role:String,
  content:String
)

case class Chat(
  messages:Seq[ChatMessage],
  
  oid:Option[String] = None,
  model:Option[String] = None,

  ts0:Long = System.currentTimeMillis(),
  ts:Long = System.currentTimeMillis(),

  tags:Seq[String] = Seq(),    
  meta: Map[String,Map[String,Any]] = Map()
) {

  def +(q:String):Chat = {
    this.copy(messages = this.messages :+ ChatMessage("user",q))
  }

  def last:ChatMessage = {
    this.messages.last
  }
}
