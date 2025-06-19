package io.syspulse.skel.tools

import scala.collection.immutable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}
// import com.typesafe.scalalogging.Logger

import upickle._
import os._

// import io.jvm.uuid._
// import io.syspulse.skel.util.Util

abstract class HttpServerable extends cask.MainRoutes{

  override def port = sys.env.get("PORT").getOrElse("8300").toInt
  override def host = sys.env.get("HOST").getOrElse("0.0.0.0")

  val url = sys.env.get("HOST").getOrElse("/api/v1/tools")

  var requests:Seq[()=>String] = Seq()
  var current = 0
  val delay = sys.env.get("DELAY").map(_.toLong).getOrElse(250L)

  override def main(args0: Array[String]) = {
    if(args0.size>0) Console.err.println(s"${args0.mkString(",")}")

    val args = 
      if(args0.size>0)
        if(args0(0).size==0)
          args0.tail
        else
          args0
      else
        args0

    val (reqs) = args.toList match {            
      case Nil => 
        Seq(() => s"""{"ts": ${System.currentTimeMillis}, "status": 100}\n""")
      case reqs =>         
        reqs.map(f => f.split("://").toList match {
          case "file" :: file :: Nil =>
            () => os.read(os.Path(f,os.pwd))
          case rsp :: Nil => () => rsp
        })
    }

    this.requests = reqs

    super.main(args)
  }    

  val CORS = Seq("Access-Control-Allow-Origin" -> "*")
  
  // =================================================== UserProfile ===
  // case class UserProfileRequest(userId:String)
  // case class UserProfileResponse(userId:String,name:String,email:String)
  // def UserProfileNoneResponse(userId:String="") = UserProfileResponse(userId,"","")
  // implicit val userProfileRequestRW: upickle.default.ReadWriter[UserProfileRequest] = upickle.default.macroRW
  // implicit val userProfileResponseRW: upickle.default.ReadWriter[UserProfileResponse] = upickle.default.macroRW
  

  // @cask.get("/api/v1/user/:userId")
  // def userProfile(userId:String,retry:Int=3,delay:Int=1000) = {
  //   println(s"\n${userId}")

  //   val u = retrying(3,250L,None,
  //     { userProfileDB.get(UUID.fromString(userId)) }
  //   )

  //   val r = 
  //     if(u.isDefined)
  //       UserProfileResponse(userId,name = u.get.name,email = u.get.email)
  //     else 
  //       UserProfileNoneResponse(userId = userId)
        
  //   println(s"User: ${userId}: ${r}")

  //   cask.Response(upickle.default.write(r),headers=CORS)
  // }

  // @cask.post("/api/v1/user")
  // def userProfile(request: cask.Request) = {
  //   println(s"\n${request}")

  //   val u:UserProfile = upickle.default.read[UserProfile](request.text)
        
  //   println(s"user: ${u}")

  //   UserProfile.add(u)

  //   cask.Response(upickle.default.write(u),headers=CORS)
  // }

  
  @cask.get("/")
  def rootGet() = {
    Console.err.println(s"<- GET")
    if(current >= requests.size)
      current = 0

    val rsp = requests(current)()
    Console.err.println(s"[${rsp}] -> ")
    
    //cask.Response(rsp,headers=CORS)
    current = current + 1
    rsp
  }

  @cask.post("/")
  def rootPort(req: cask.Request) = {
    Console.err.println(s"<<< POST")
    Console.err.println(s"<<< Headers:\n${req.headers}")
    Console.err.println(s"<<< Body:\n")
    Console.println(req.text())
    if(current >= requests.size)
      current = 0

    val rsp = requests(current)()
    Console.err.println(s"[${rsp}] -> ")
    
    //cask.Response(rsp,headers=CORS)
    current = current + 1
    rsp
  }

  @cask.post(url)
  def portCustom(req: cask.Request) = {
    Console.err.println(s"<<< POST")
    Console.err.println(s"<<< Headers:\n${req.headers}")
    Console.err.println(s"<<< Body:\n")
    Console.println(req.text())
    if(current >= requests.size)
      current = 0

    val rsp = requests(current)()
    Console.err.println(s"[${rsp}] -> ")
    
    current = current + 1
    cask.Response(rsp,headers=CORS)
  }

  @cask.route(url, methods = Seq("options"))
  def cors(req: cask.Request) = {
    Console.err.println(s"<<< OPTIONS")
    Console.err.println(s"<<< Headers:\n${req.headers}")
    Console.err.println(s"<<< Body:\n")
    Console.println(req.text())
    
    cask.Response("",headers=CORS)
  }

  @cask.staticFiles("/web",headers = Seq("Cache-Control" -> "max-age=14400"))
  def staticFileRoutes() = "web"

  @cask.get("/sse")
  def streamGet() = streamSse(None)

  @cask.post("/sse")
  def streamPost(req: cask.Request) = {
    Console.err.println(s"<<< POST STREAM")
    Console.err.println(s"<<< Headers:\n${req.headers}")
    Console.err.println(s"<<< Body:\n")
    Console.println(req.text())
    streamSse(Some(req.text()))
  }

  private def streamSse(requestData: Option[String]) = {
    val isPost = requestData.isDefined
    val method = if (isPost) "POST" else "GET"
    Console.err.println(s"<- $method STREAM")
    
    val streamId = s"stream-${System.currentTimeMillis()}"
    val model = "gpt-4-streaming"
    val created = System.currentTimeMillis() / 1000
    
    val steps = if (isPost) {
      val data = requestData.get
      Seq(
        ("received", s"Request received: $data"),
        ("validating", "Validating request"),
        ("processing", "Processing request"),
        ("finalizing", "Finalizing response"),
        ("completed", "Request completed successfully")
      )
    } else {
      Seq(
        ("initializing", "Stream started"),
        ("processing", "Processing step 1"),
        ("processing", "Processing step 2"), 
        ("processing", "Processing step 3"),
        ("completed", "Stream completed")
      )
    }
    
    // Create a streaming response that sends chunks immediately
    val streamResponse = new cask.Response(
      new java.io.InputStream {
        private var currentStep = 0
        private var currentChunk = ""
        private var chunkIndex = 0
        private var sentDone = false
        
        private def createChunk(state: String, message: String, isLast: Boolean = false): String = {
          val json = if (isLast) {
            s"""{"id":"$streamId","object":"stream.chunk","created":$created,"model":"$model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
          } else {
            s"""{"id":"$streamId","object":"stream.chunk","created":$created,"model":"$model","choices":[{"index":0,"delta":{"content":"$state: $message"},"finish_reason":null}]}"""
          }
          s"data: $json\n\n"
        }
        
        private def createDoneChunk(): String = "data: [DONE]\n\n"
        
        override def read(): Int = {
          if (currentChunk.isEmpty) {
            if (currentStep >= steps.length) {
              if (!sentDone) {
                // Send [DONE] chunk
                currentChunk = createDoneChunk()
                chunkIndex = 0
                sentDone = true
                Console.err.println(s"<- $method STREAM: Sending [DONE]")
              } else {
                // Already sent [DONE], close connection
                Console.err.println(s"<- $method STREAM: Closing connection after [DONE]")
                return -1
              }
            } else {
              val (state, message) = steps(currentStep)
              val isLast = currentStep == steps.length - 1
              
              Console.err.println(s"<- $method STREAM: ${currentStep + 1}/${steps.length} - $state: $message")
              
              currentChunk = createChunk(state, message, isLast)
              chunkIndex = 0
              currentStep += 1
              
              // Simulate delay between chunks (like OpenAI's token generation)
              Thread.sleep(delay)
            }
          }
          
          if (chunkIndex >= currentChunk.length) {
            currentChunk = ""
            return -1
          }
          
          val byte = currentChunk.charAt(chunkIndex).toByte
          chunkIndex += 1
          byte
        }
      },
      statusCode = 200,
      headers = CORS ++ Seq(
        "Content-Type" -> "text/event-stream",
        "Cache-Control" -> "no-cache",
        "Connection" -> "keep-alive",
        "Transfer-Encoding" -> "chunked"
      ),
      cookies = Seq()
    )
    
    streamResponse
  }

  Console.err.println(s"Listening on ${host}:${port}...")
  initialize()
}

object HttpServer extends HttpServerable {
  
}
