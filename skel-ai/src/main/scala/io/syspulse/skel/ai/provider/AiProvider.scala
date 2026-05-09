package io.syspulse.skel.ai.provider

import scala.util.{Try, Success, Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.Source

import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest,HttpResponse,HttpEntity,ContentTypes}
import akka.http.scaladsl.model.{HttpMethods,StatusCodes}
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString

import io.syspulse.skel.ai.{Ai,Chat}
import io.syspulse.skel.ai.ChatMessage
import io.syspulse.skel.ai.core.{AiURI,AiTool}
import io.syspulse.skel.ai.core.{OpenAiURI,VeniceURI,GrokURI,GeminiURI,ClaudeURI,DeepseekURI,OpenRouterURI,MirrorURI,IkaURI}
import io.syspulse.skel.ai.provider.openai.OpenAi
import io.syspulse.skel.ai.provider.venice.VeniceAi
import io.syspulse.skel.ai.provider.grok.GrokAi
import io.syspulse.skel.ai.provider.gemini.GeminiAi
import io.syspulse.skel.ai.provider.claude.ClaudeAi
import io.syspulse.skel.ai.provider.deepseek.DeepseekAi
import io.syspulse.skel.ai.provider.openrouter.OpenRouterAi
import io.syspulse.skel.ai.provider.mirror.MirrorAi
import io.syspulse.skel.ai.provider.ika.IkaAi

trait AiProvider {  
  val log = Logger(s"${this}")

  // Lazy ActorSystem - created once and reused
  protected lazy val httpSystem = ActorSystem("AiProvider")
  protected implicit lazy val httpEc = httpSystem.dispatcher
  protected implicit lazy val httpMat = SystemMaterializer(httpSystem).materializer
  
  // Helper method for HTTP requests using Akka HTTP
  protected def httpRequest(url: String, body: String, headers: Seq[(String, String)], timeout: Long): Future[HttpResponse] = {
    // Filter out Content-Type as it's set via HttpEntity
    val filteredHeaders = headers.filterNot { case (k, _) => k.equalsIgnoreCase("Content-Type") }
    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = url,
      entity = HttpEntity(ContentTypes.`application/json`, body),
      headers = filteredHeaders.map { case (k, v) => RawHeader(k, v) }
    )
    Http()(httpSystem).singleRequest(httpRequest)
  }
  
  // Helper to read response body as string
  protected def readResponseBody(response: HttpResponse): Future[String] = {
    import akka.stream.scaladsl.Sink
    response.entity.dataBytes
      .runWith(Sink.fold(ByteString.empty)(_ ++ _))
      .map(_.utf8String)
  }    

  def getUri():AiURI
  def getTimeout():Long = getUri().timeout
  def getRetry():Int = getUri().retry
  def getModel():Option[String] = getUri().getModel()

  // single question (no context)
  def ask(question:String,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai]  
  // chat (with context by Chat)
  def chat(chat:Chat,model:Option[String],system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Chat]
  
  // prompt (with context by Provider)
  def prompt(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai]
  def promptAsync(ai:Ai,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None)(implicit ec: ExecutionContext):Future[Ai]

  // prompt (with context by Provider)
  def promptStream(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None):Try[Ai]
  def promptStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,images:Seq[String] = Seq.empty,outputType:Option[String] = None)(implicit ec: ExecutionContext):Future[Ai]

  /** Anthropic Messages API (`POST /v1/messages`); supported for `claude://` only unless overridden. */
  def messages(ai: Ai, system: Option[String] = None, timeout: Long = getTimeout(), retry: Int = getRetry(), tools: Seq[AiTool] = Seq.empty, images: Seq[String] = Seq.empty, outputType: Option[String] = None): Try[Ai] =
    Failure(new UnsupportedOperationException("messages-stream API is not supported"))

  def messagesAsync(ai: Ai, system: Option[String] = None, timeout: Long = getTimeout(), retry: Int = getRetry(), tools0: Seq[AiTool] = Seq.empty, images: Seq[String] = Seq.empty, outputType: Option[String] = None)(implicit ec: ExecutionContext): Future[Ai] =
    Future.failed(new UnsupportedOperationException("messages-stream API is not supported"))

  def messagesStream(ai: Ai, onEvent: String => Unit, system: Option[String] = None, timeout: Long = getTimeout(), retry: Int = getRetry(), tools: Seq[AiTool] = Seq.empty, images: Seq[String] = Seq.empty, outputType: Option[String] = None): Try[Ai] =
    Failure(new UnsupportedOperationException("messages-stream API is not supported"))

  def messagesStreamAsync(ai: Ai, onEvent: String => Unit, system: Option[String] = None, timeout: Long = getTimeout(), retry: Int = getRetry(), tools0: Seq[AiTool] = Seq.empty, images: Seq[String] = Seq.empty, outputType: Option[String] = None)(implicit ec: ExecutionContext): Future[Ai] =
    Future.failed(new UnsupportedOperationException("messages-stream API is not supported"))

  //def toolsStreamAsync(ai:Ai,onEvent: (String) => Unit,system:Option[String] = None,timeout:Long = 10000,retry:Int = 3)(implicit ec: ExecutionContext):Future[Ai]
  def askStream(ai:Ai,
    instructions:Option[String] = None,
    onEvent: (String) => Unit = (s) => {},
    onData: (String) => Unit = (s) => {},
    onError: (String) => Unit = (s) => {},
    onDone: () => Unit = () => {},
    timeout:Long = getTimeout(),retry:Int = getRetry(),tools:Seq[AiTool] = Seq.empty,outputType:Option[String] = None)
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
      case uri:MirrorURI => new MirrorAi(uri)
      case uri:IkaURI => new IkaAi(uri)
      case p => 
        Console.err.println(s"Unknown AI provider: '${p}'")
        sys.exit(1)
    }
  }
}

