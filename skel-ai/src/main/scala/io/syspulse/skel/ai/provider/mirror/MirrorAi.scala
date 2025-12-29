package io.syspulse.skel.ai.provider.mirror

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.sse.ServerSentEvent

import io.syspulse.skel.ai.{Ai,Chat}
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.core.{AiURI,AiTool}
import io.syspulse.skel.ai.core.MirrorURI
import io.syspulse.skel.ai.provider.AiProvider
import io.syspulse.skel.ai.core.AiURI
import scala.concurrent.ExecutionContext
import io.syspulse.skel.util.Util

class MirrorAi(uri:MirrorURI) extends AiProvider {
  override def getUri():AiURI = uri
  override def getTimeout():Long = uri.timeout
  override def getRetry():Int = uri.retry
  override def getModel():Option[String] = uri.getModel()

  override def ask(question:String,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai] = {
    val answer = getModel() match {
      case Some("hash") => Util.sha256(question)
      case _ => question
    }

    Success(Ai(
      question = question,
      answer = Some(answer),
      oid = Some(MirrorURI.ID),
      model = model
    ))
  }

  override def chat(chat:Chat,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Chat] = {
    // Mirror: return the last user message as assistant response
    val lastMessage = if(chat.messages.nonEmpty) chat.messages.last.content else ""
    val mirroredAnswer = getModel() match {
      case Some("hash") => Util.sha256(lastMessage)
      case _ => lastMessage
    }
    val response = ChatMessage("assistant", mirroredAnswer)
    Success(chat.copy(messages = chat.messages :+ response))
  }

  override def prompt(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai] = {
    // Mirror: return the question as the answer
    val answer = getModel() match {
      case Some("hash") => Util.sha256(ai.question)
      case _ => ai.question
    }
    Success(ai.copy(answer = Some(answer), oid = Some(MirrorURI.ID)))
  }

  override def promptAsync(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None)(implicit ec: ExecutionContext):Future[Ai] = {
    // Mirror: return the question as the answer
    val answer = getModel() match {
      case Some("hash") => Util.sha256(ai.question)
      case _ => ai.question
    }
    Future.successful(ai.copy(answer = Some(answer), oid = Some(MirrorURI.ID)))
  }

  override def promptStream(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai] = {
    // Mirror: stream the question back character by character
    val answer = getModel() match {
      case Some("hash") => Util.sha256(ai.question)
      case _ => ai.question
    }
    answer.foreach(c => onEvent(c.toString))
    Success(ai.copy(answer = Some(answer), oid = Some(MirrorURI.ID)))
  }

  override def promptStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None)(implicit ec: ExecutionContext):Future[Ai] = {
    // Mirror: stream the question back character by character
    val answer = getModel() match {
      case Some("hash") => Util.sha256(ai.question)
      case _ => ai.question
    }
    Future {
      answer.foreach(c => onEvent(c.toString))
      ai.copy(answer = Some(answer), oid = Some(MirrorURI.ID))
    }
  }
  
  override def askStream(ai:Ai,instructions:Option[String] = None,onEvent: (String) => Unit = (s) => {},onData: (String) => Unit = (s) => {},onError: (String) => Unit = (s) => {},onDone: () => Unit = () => {},timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,outputType:Option[String] = None)(implicit ec: ExecutionContext,sys: ActorSystem): Source[ServerSentEvent, Any] = {
    // Mirror: stream the question back
    val answer = getModel() match {
      case Some("hash") => Util.sha256(ai.question)
      case _ => ai.question
    }
    Source.single(ServerSentEvent(answer))
  }
}