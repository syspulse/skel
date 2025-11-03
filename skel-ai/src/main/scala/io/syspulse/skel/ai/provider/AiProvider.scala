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
import io.syspulse.skel.ai.core.{AiURI,AiTool}
import io.syspulse.skel.ai.core.{OpenAiURI,VeniceURI,GrokURI,GeminiURI,ClaudeURI,DeepseekURI,OpenRouterURI}
import io.syspulse.skel.ai.provider.openai.OpenAi
import io.syspulse.skel.ai.provider.venice.VeniceAi
import io.syspulse.skel.ai.provider.grok.GrokAi
import io.syspulse.skel.ai.provider.gemini.GeminiAi
import io.syspulse.skel.ai.provider.claude.ClaudeAi
import io.syspulse.skel.ai.provider.deepseek.DeepseekAi
import io.syspulse.skel.ai.provider.openrouter.OpenRouterAi

trait AiProvider {  
  val log = Logger(s"${this}")

  def getUri():AiURI
  def getTimeout():Long = getUri().timeout
  def getRetry():Int = getUri().retry
  def getModel():Option[String]

  // single question (no context)
  def ask(question:String,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty):Try[Ai]  
  // chat (with context by Chat)
  def chat(chat:Chat,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty):Try[Chat]
  
  // prompt (with context by Provider)
  def prompt(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty):Try[Ai]
  def promptAsync(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext):Future[Ai]

  // prompt (with context by Provider)
  def promptStream(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty):Try[Ai]
  def promptStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty)(implicit ec: ExecutionContext):Future[Ai]

  //def toolsStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3)(implicit ec: ExecutionContext):Future[Ai]
  def askStream(ai:Ai,
    instructions:Option[String] = None,
    onEvent: (String) => Unit = (s) => {},
    onData: (String) => Unit = (s) => {},
    onError: (String) => Unit = (s) => {},
    onDone: () => Unit = () => {},
    timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty)
    (implicit ec: ExecutionContext,sys: ActorSystem): Source[ServerSentEvent, Any]
}

object AiProvider {
  def apply(uri:String):AiProvider = {
    val aiUri = AiURI(uri)
    apply(aiUri)
  }

  def apply(uri:AiURI):AiProvider = {
    uri match {
      case uri:OpenAiURI => new OpenAi(uri)
      case uri:VeniceURI => new VeniceAi(uri)
      case uri:GrokURI => new GrokAi(uri)
      case uri:GeminiURI => new GeminiAi(uri)
      case uri:ClaudeURI => new ClaudeAi(uri)
      case uri:DeepseekURI => new DeepseekAi(uri)
      case uri:OpenRouterURI => new OpenRouterAi(uri)
      case p => 
        Console.err.println(s"Unknown AI provider: '${p}'")
        sys.exit(1)
    }
  }
}

