package io.syspulse.skel.ai.provider

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.sse.ServerSentEvent

import io.syspulse.skel.ai.{Ai,Chat}
import io.syspulse.skel.ai.ChatMessage

case class AiTool(
  name: String,
  description: Option[String] = None,
  parameters: Map[String, Any] = Map.empty,
  strict: Option[Boolean] = None
)


trait AiProvider {  
  val log = Logger(s"${this}")

  def getTimeout():Long = 10000L
  def getRetry():Int = 3
  def getModel():Option[String]

  // single question (no context)
  def ask(question:String,model:Option[String],system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Ai]  
  // chat (with context by Chat)
  def chat(chat:Chat,model:Option[String],system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Chat]
  
  // prompt (with context by Provider)
  def prompt(ai:Ai,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Ai]
  def promptAsync(ai:Ai,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3)(implicit ec: ExecutionContext):Future[Ai]

  // prompt (with context by Provider)
  def promptStream(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3):Try[Ai]
  def promptStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3,tools:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext):Future[Ai]

  //def toolsStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3)(implicit ec: ExecutionContext):Future[Ai]
  def askStream(ai:Ai,
    instructions:Option[String] = None,
    onEvent: (String) => Unit = (s) => {},
    onData: (String) => Unit = (s) => {},
    onError: (String) => Unit = (s) => {},
    onDone: () => Unit = () => {},
    timeout:Long = 10000,retry:Int = 3,tools:Seq[AiTool] = Seq.empty)
    (implicit ec: ExecutionContext,sys: ActorSystem): Source[ServerSentEvent, Any]
}

