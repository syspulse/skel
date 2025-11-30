package io.syspulse.skel.ingest.flow

import scala.util.{Success,Failure,Try}

import scala.jdk.CollectionConverters._

import java.nio.file.StandardOpenOption._
import java.nio.file.{Paths,Files}

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.Http
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import java.nio.file.{Path,Paths, Files}

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random

import akka.stream.alpakka.file.scaladsl.Directory
import akka.stream.alpakka.file.scaladsl.LogRotatorSink
import akka.stream.alpakka.file.scaladsl.FileTailSource

import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.ElasticsearchParams

// Slick !!!
// import akka.stream.alpakka.slick.scaladsl._
// import akka.stream.scaladsl._
// import slick.jdbc.GetResult
// import slick.basic.DatabaseConfig
// import slick.jdbc.JdbcProfile

// Quill
import io.getquill._
import io.getquill.context._
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

// Redis
import scredis.Redis
import scredis.Client
import scredis.protocol.AuthConfig
import scredis.RedisConfig
import scredis.PubSubMessage
import io.syspulse.skel.uri.RedisURI

import io.syspulse.skel
import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util
import io.syspulse.skel.util.TimeUtil
import io.syspulse.skel.uri.ElasticURI
import io.syspulse.skel.uri.KafkaURI
import io.syspulse.skel.uri.TwitterURI
import io.syspulse.skel.uri.AkkaURI
import io.syspulse.skel.elastic.ElasticClient
import io.syspulse.skel.twitter.FromTwitter

import spray.json.JsonFormat
import java.nio.file.StandardOpenOption
import java.nio.file.OpenOption
import java.nio.file.FileVisitOption
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.common.EntityStreamingSupport
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._

import scala.concurrent.Promise
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import akka.actor.Cancellable
import java.util.TimeZone
import java.time.ZoneId

import com.github.mjakubowski84.parquet4s.{ParquetReader, ParquetWriter}
import org.apache.parquet.hadoop.ParquetFileWriter.Mode
import org.apache.parquet.hadoop.metadata.CompressionCodecName
import org.apache.parquet.hadoop.{ParquetWriter => HadoopParquetWriter}
import org.apache.hadoop.conf.Configuration
import com.github.mjakubowski84.parquet4s.ValueEncoder
import com.github.mjakubowski84.parquet4s.ParquetRecordEncoder
import com.github.mjakubowski84.parquet4s.ParquetSchemaResolver
import com.github.mjakubowski84.parquet4s.ParquetStreams
import akka.stream.alpakka.file.DirectoryChange
import akka.stream.alpakka.file.impl.DirectoryChangesSource
import akka.stream.alpakka.file
import java.net.InetSocketAddress

import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.PathMatcher0
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.HttpResponse
import io.getquill.context.jdbc.JdbcContext
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.HttpMethod
import akka.util.Timeout
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.actor.ActorNotFound
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.model.HttpHeader
import scredis.RedisConfigDefaults
import java.util.Base64

object Flows extends Flows {

}

trait Flows {
  private val log = Logger(this.toString)

  val retrySettingsDefault = RestartSettings(
    minBackoff = FiniteDuration(1000L,TimeUnit.MILLISECONDS),
    maxBackoff = FiniteDuration(9000L,TimeUnit.MILLISECONDS),
    randomFactor = 0.2
  )

  // NOTE: https://github.com/akka/akka-http/issues/4128
  // WARNING: Nobody understands how akka http works except Lightbend          
  def retry_1_deterministic(as:ActorSystem,timeout:FiniteDuration) = ConnectionPoolSettings(as)
                          .withBaseConnectionBackoff(FiniteDuration(1000,TimeUnit.MILLISECONDS))
                          .withMaxConnectionBackoff(FiniteDuration(1000,TimeUnit.MILLISECONDS))
                          .withMaxConnections(1)
                          .withMaxRetries(1)
                          // .withMaxOpenRequests(1)
                          .withConnectionSettings(ClientConnectionSettings(as)
                            .withIdleTimeout(timeout)
                            .withConnectingTimeout(timeout))

  def fromSourceRestart(s:Source[ByteString, _],retry:RestartSettings = retrySettingsDefault) = RestartSource.onFailuresWithBackoff(retry) { () =>
    log.info(s"Restarting -> Source(${s})...")
    s
  }

  def toSinkRestart[T <: Ingestable](s:Sink[Ingestable,_],retry:RestartSettings = retrySettingsDefault) = 
    RestartSink.withBackoff[T](retry) { () =>
      log.info(s"Restarting -> Sink(${s})...")
      s
    }

  def formatter[O <: Ingestable](o:O,format:String,nl:String="")(implicit fmt:JsonFormat[O]):ByteString = {
    if(o == null) return ByteString()
    
    import spray.json._
    format match {
      case "raw" => ByteString(o.toRaw)
      case "jsonp" => ByteString(s"${o.toJson.prettyPrint}${nl}")
      case "json" => ByteString(s"${o.toJson.compactPrint}${nl}")
      case "csv" => ByteString(s"${o.toCSV}${nl}")
      case _ => ByteString(s"${o.toString}${nl}")
    }
  }

// ==================================================================================================
// Sources
// ==================================================================================================  

  def fromCron(expr:String)(implicit as:ActorSystem):Source[ByteString, _] = {

    // if expr is just number treat is as milliseconds and use fromClock
    try {
      expr.toLong
      return fromClock(expr)
    } catch {
      case _ =>
    }

    import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension 
    //import akka.stream.typed.scaladsl.ActorSource

    val cronSource = Source.actorRef[ByteString](
      bufferSize = 100,
      overflowStrategy = OverflowStrategy.dropHead //OverflowStrategy.fail // <- convenient for testing
    ).map(t => {
        log.info(s"tick: ${t.utf8String}")
        //ByteString(s"${System.currentTimeMillis()}\n")
        ByteString(s"${System.currentTimeMillis()}")
    })

    val (cronActor,cronSourceMat) = cronSource.preMaterialize() //.to(Sink.foreach(println)).run()
    log.info(s"actor: ${cronActor}")
    log.info(s"source: ${cronSource}")

    val sched = 
      if(expr.contains("_") || expr.contains("*")) {
        try { 
          val name = s"job-${System.currentTimeMillis()}"
          val job = QuartzSchedulerExtension(as).createSchedule( 
            name , Some("job"),
            expr.replaceAll("_"," "),
            timezone = TimeZone.getTimeZone(ZoneId.of("UTC"))
          )
          
          QuartzSchedulerExtension(as).schedule(name, cronActor, ByteString())
        } catch {
          case iae: IllegalArgumentException => iae // Do something useful with it.
        }	
      } else
        QuartzSchedulerExtension(as).schedule(expr, cronActor, ByteString())

    log.info(s"sched: ${sched}")
    //Future { for( i <- Range(0,1000)) { Thread.sleep(1000); cronActor ! ByteString(s"${i}\n") } }

    cronSourceMat
  }

  def fromRand(expr:String) = {
    def randHex(n:Int) = ByteString(Util.hex(Random.nextBytes(n),false))
    def randBase64(n:Int) = ByteString(Base64.getEncoder.encodeToString(Random.nextBytes(n)))
    def randStr(n:Int) = ByteString(Random.nextString(n))
    
    // Base58 alphabet (excludes 0, O, I, l to avoid confusion)
    val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz_"
    def randBase58(n:Int) = {
      val sb = new StringBuilder(n)
      for (_ <- 0 until n) {
        sb.append(base58Chars.charAt(Random.nextInt(base58Chars.length)))
      }
      ByteString(sb.toString)
    }
        
    // Use lazy evaluation - generate random value on materialization
    Source.fromIterator(() => Iterator.single(
      expr.split(":").toList match {        
        case "hex" :: n :: Nil => randHex(n.toInt)        
        case "hex" :: Nil => randHex(32)
        case "base64" :: n :: Nil => randBase64(n.toInt)
        case "base64" :: Nil => randBase64(32)
        case ("base58" | "txt") :: n :: Nil => randBase58(n.toInt)
        case ("base58" | "txt") :: Nil => randBase58(58)
        case "str" :: n :: Nil => randStr(n.toInt)
        case "str" :: Nil => randStr(32)
        case n :: Nil => randHex(n.toInt)        
        case _ => randHex(32)
      }
    ))
  }

  def fromClock(expr:String) = {
    val msec = TimeUtil.humanToMillis(expr) //expr.toLong //Duration.create(expr).toMillis
    val freq = FiniteDuration(msec,TimeUnit.MILLISECONDS)
    Source
      .tick(FiniteDuration(150L,TimeUnit.MILLISECONDS),freq,"") // initial small delay (0 does not work)
      .map(_ => ByteString(s"${System.currentTimeMillis()}"))      
  }

  def toNull[T <: Ingestable](delay:Long = 0L)(implicit fmt:JsonFormat[T]) = {
    if(delay > 0L)
      Flow[T]
        .throttle(1,FiniteDuration(delay,TimeUnit.MILLISECONDS))
        .toMat(Sink.foreach(println))(Keep.right)
    else
      Sink.ignore
  }

  def fromNull = Source.future(Future({Thread.sleep(Long.MaxValue);ByteString("")})) //Source.never

  def fromHttpFuture(req: HttpRequest)(implicit as:ActorSystem,timeout:FiniteDuration) = 
    Http()
    .singleRequest(req, settings = retry_1_deterministic(as,timeout))
    .flatMap(res => { 
      res.status match {
        case StatusCodes.OK => 
          val body = res.entity.dataBytes.runReduce(_ ++ _)
          Future(Source.future(body))
        case _ => 
          val body = Await.result(res.entity.dataBytes.runReduce(_ ++ _),FiniteDuration(1000L,TimeUnit.MILLISECONDS)).utf8String
          log.error(s"${req}: ${res.status}: body=${body}")
          throw new Exception(s"${req}: ${res.status}")
          // not really reachable... But looks extra-nice :-/
          Future(Source.future(Future(ByteString(body))))
      }      
    })
      
  def fromHttp(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192, retry:Option[RestartSettings]=Some(retrySettingsDefault))(implicit as:ActorSystem,timeout:FiniteDuration) = {
    //val s = Source.future(fromHttpFuture(req))
    val s = if(retry.isDefined)
      RestartSource.onFailuresWithBackoff(retry.get) { () =>
        log.info(s"${retry.get}: ==> ${req}")
        Source.futureSource {
          this.fromHttpFuture(req)
        }
      }
    else
      Source.futureSource {
        this.fromHttpFuture(req)
      }
      
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))    
  }

  def fromHttpRestartable(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    fromHttp(req,frameDelimiter,frameSize,Some(retry))
  }

  def fromHttpList(reqs: Seq[HttpRequest],par:Int = 1, frameDelimiter:String="\n",frameSize:Int = 8192,throttle:Long = 10L,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    // Http().singleRequest does not respect mapAsync for parallelization !!!
    val s = 
      Source(reqs)
      .throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .flatMapConcat(req => this.fromHttpRestartable(req,frameDelimiter,frameSize,retry))
    
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))

    // fromSourceRestart(s)
    s
  }

  def fromHttpListAsFlow(reqs: Seq[HttpRequest],par:Int = 1, frameDelimiter:String="\n",frameSize:Int = 8192,throttle:Long = 10L)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    val f1 = Flow[String]      
      .mapConcat(_ => {
        reqs
      })
      .throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .flatMapConcat(req => {
        log.info(s"--> ${req}")
        //Flows.fromHttpFuture(req)(as)
        this.fromHttpRestartable(req, frameDelimiter, frameSize)
      })      
    
    if(frameDelimiter.isEmpty())
      f1
    else
      f1.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def fromStdin(frameDelimiter:String="\n",frameSize:Int = 8192):Source[ByteString, Future[IOResult]] = {
    val s = StreamConverters.fromInputStream(() => System.in)
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  // this is non-streaming simple ingester. Reads full file, flattens it and parses into Stream of Tms objects
  def fromFile(file:String = "/dev/stdin",chunk:Int = 0,frameDelimiter:String="\r\n",frameSize:Int = 8192):Source[ByteString, Future[IOResult]] =  {
    val filePath = Util.pathToFullPath(file)
    val s = FileIO
      .fromPath(Paths.get(filePath),chunkSize = if(chunk==0) Files.size(Paths.get(filePath)).toInt else chunk)
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def fromDir(dir:String,depth:Int=0,chunk:Int = 0,frameDelimiter:String="",frameSize:Int = 1024 * 1024 * 1024) =  {
    val log = Logger(s"${this}")

    val dirPath = Util.pathToFullPath(dir)
    val s1 = if(depth==0) 
      Directory.ls(Paths.get(dirPath)) 
    else 
      Directory.walk(Paths.get(dirPath), maxDepth = Some(depth), List(FileVisitOption.FOLLOW_LINKS))

    val s = s1
      .filter(p => p.toFile.isFile())
      .flatMapConcat( p => {
        log.info(s"file: ${p}")
        val fs = FileIO.fromPath(p,chunkSize = if(chunk==0) Files.size(p).toInt else chunk)
        
        // delimiting should be here otherwise file without delimiters will not progress
        if(frameDelimiter.isEmpty())
          fs
        else
          fs.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
      })
    
    // if(frameDelimiter.isEmpty())
    //   s
    // else
    //   s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
    s
  }

  def fromTail(file:String,chunk:Int = 1024 * 1024,frameDelimiter:String="\r\n",frameSize:Int = 8192,
              retry:Option[RestartSettings]=Some(retrySettingsDefault)):Source[ByteString, NotUsed] =  {

    val filePath = Util.pathToFullPath(file)
    
    val s0 = FileTailSource.apply(
      path = Paths.get(filePath),
      maxChunkSize = chunk,
      startingPosition = 0,
      pollingInterval = FiniteDuration(250,TimeUnit.MILLISECONDS)
    )
    
    val s = 
      if(retry.isDefined) RestartSource.onFailuresWithBackoff(retry.get) { () =>
        log.info(s"tail: ${file}")
        // this is a trick because FileTailSource is stupid to fail in preStart instead of flow, so exception will stop the stream
        // https://www.signifytechnology.com/blog/2019/11/akka-streams-pitfalls-to-avoid-part-1-by-jakub-dziworski
        // Unfortunately FileTailSource is dumb and does not detect file truncations (like `tail`)
        Source.single("").flatMapConcat(_ => s0)
      } else
        s0
      
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def fromDirTail(dir:String,chunk:Int = 1024 * 1024,frameDelimiter:String="\r\n",frameSize:Int = 8192,retry:RestartSettings=retrySettingsDefault):Source[ByteString, NotUsed] = {
    val maxFiles = 1000
    val sourceTail = 
      file.scaladsl.DirectoryChangesSource(Paths.get(dir), pollInterval = FiniteDuration(1000,TimeUnit.MILLISECONDS), maxBufferSize = maxFiles)
        // only watch for file creation events (5)
        .collect { case (path, DirectoryChange.Creation) => path }
        .map { path:Path =>
          log.info(s"File detected: '${path}'")
          fromTail(path.toFile.toString,chunk,frameDelimiter,frameSize,None)          
        }
      
    sourceTail.flatMapMerge(maxFiles, identity)
  }

  def fromTcpClientUri(uri:String,
    chunk:Int = 0,frameDelimiter:String="\r\n",frameSize:Int = 8192,
    connectTimeout:Long=1000L,idleTimeout:Long=1000L * 60L * 60L,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem):Source[ByteString,NotUsed] = {
      uri.split(":").toList match {
        case host :: port :: Nil => 
          tcpClient(host,port.toInt,chunk,frameDelimiter,frameSize,connectTimeout,idleTimeout,retry)
        case _ => 
          throw new Exception(s"invalid uri (port missing): ${uri}")
      }
  }

  def fromTcpClient(host:String, port:Int,
    chunk:Int = 0,frameDelimiter:String="\r\n",frameSize:Int = 8192,
    connectTimeout:Long=1000L,idleTimeout:Long=1000L * 60L * 60L,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem):Source[ByteString,NotUsed] = 
      tcpClient(host,port,chunk,frameDelimiter,frameSize,connectTimeout,idleTimeout,retry)

  def tcpClient(host:String,port:Int,
    chunk:Int,frameDelimiter:String,frameSize:Int,
    connectTimeout:Long,idleTimeout:Long,retry:RestartSettings)(implicit as:ActorSystem):Source[ByteString,NotUsed] = {
    
    val ip = InetSocketAddress.createUnresolved(host, port)
    val conn = Tcp().outgoingConnection(
      remoteAddress = ip,
      connectTimeout = Duration(connectTimeout,TimeUnit.MILLISECONDS),
      idleTimeout = Duration(idleTimeout,TimeUnit.MILLISECONDS)
    )
    val s0 = Source.actorRef(1, OverflowStrategy.fail)
        .via(conn)
    val s = RestartSource.withBackoff(retry) { () => 
      log.info(s"Connecting -> tcp://${host}:${port}")
      s0
    }

    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def uriToHostPort(uri:String) = {
    val (host,port,suffix) = uri.split("[:/]").toList match {      
        case h :: p :: Nil => (h,p.toInt,"")
        case h :: p :: s => (h,p.toInt,s.mkString("/"))
        case h :: Nil => (h,8080,"")
        case _ => throw new Exception(s"invalid uri: ${uri}")
      }
    (host,port,suffix)
  }

  def fromHttpServer(uri:String,chunk:Int = 0, frameDelimiter:String="\n",frameSize:Int = 8192, retry:RestartSettings=retrySettingsDefault)(implicit sys:ActorSystem,timeout:FiniteDuration) = {
    val (host,port,suffix) = uriToHostPort(uri)
    
    val (a,s0) = Source
      .actorRef[ByteString](32,OverflowStrategy.fail)
      .preMaterialize()
    
    //val fullPath = suffix.split("/").foldLeft[PathMatcher0](Slash)((r,s) => r ~ s ~ Slash)
    // not working correctly with /api/v1/path/
    val fullPath = separateOnSlashes(suffix)
    
    val route = path(fullPath) { //rawPathPrefix(fullPath) { 
      post {
        extractClientIP { addr =>  // requires: akka.http.server.remote-address-attribute = on
          entity(as[String]) { data =>
            log.debug(s"REQ: ${addr}: ${data}")
            a ! ByteString(data)
            // complete with Success !
            complete(StatusCodes.OK)
          }
        }
      }      
    }
          
    val binding = Http()
      .newServerAt(host, port)
      .bindFlow(route)

    log.info(s"Listen: http://${host}:${port}/${suffix} ...")

    val s = s0
      
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))    
  }

  def fromWebsocket(uri:String,
    frameDelimiter:String="\n",frameSize:Int=1024 * 1024,retry:RestartSettings=retrySettingsDefault,
    helloMsg:Option[String]=None,headers0:Seq[HttpHeader] = Seq())
    (implicit as:ActorSystem,timeout:FiniteDuration) = { 

    // try to parse headers
    val uriWs = skel.uri.WsURI(uri)
    val headers = headers0 ++ uriWs.ops.map{case (k,v) => 
      if(k.equalsIgnoreCase("auth"))
        RawHeader("Authorization",s"Bearer $v")
      else
        RawHeader(k,v)
    }.toSeq
    
    // ATTENTION: Server disconnect is not onFailure and must be treated as upstream completion !
    val s = RestartSource.withBackoff(retry) { () =>
      log.info(s"=> ${uriWs.url} (${retry})")      
      val ws = new FromWebsocket(uriWs.url,helloMsg = helloMsg,headers = headers)
      ws.source()
    }

    // if(frameDelimiter.isEmpty())
    //   s
    // else
    //   s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))    
    s
  }

  def fromTwitter(uri:String, frameDelimiter:String="\n",frameSize:Int=1024 * 1024, retry:RestartSettings=retrySettingsDefault)
    (implicit as:ActorSystem,timeout:FiniteDuration) = { 

    val twitter = new FromTwitter(uri)
    twitter.source(frameDelimiter,frameSize)
  }

// ==================================================================================================
// Akka
// ==================================================================================================  

  def fromAkka(uri:String,bufferSize: Int = 1000, overflowStrategy: OverflowStrategy = OverflowStrategy.backpressure)
              (implicit as:ActorSystem) = {
    
    val akkaUri = AkkaURI(uri)

    val config = ConfigFactory.parseString(s"""
      akka {
        actor {
          provider = remote
        }
        remote {
          artery {
            enabled = on
            transport = tcp
            canonical.hostname = "${akkaUri.host.getOrElse("127.0.0.1")}"
            canonical.port = ${akkaUri.port.getOrElse(2552)}
          }
        }
      }
    """)

    val system = if(akkaUri.port.isDefined) 
      ActorSystem(akkaUri.system,config)
    else
      as

    //val actorSelection = system.actorSelection(uri)
    // implicit val timeout: Timeout = FiniteDuration(akkaUri.timeout,TimeUnit.MILLISECONDS)
    val (queue, source) = Source
      .queue[ByteString](bufferSize, overflowStrategy)
      .preMaterialize()

    val sourceActor = system.actorOf(Props(new Actor {
      def receive: Receive = {
        case bs: ByteString => 
          // Handle incoming ByteString messages
          log.info(s"${self.path} <= ${bs}")
          queue.offer(bs)
        case msg => 
          log.warn(s"unexpected message: $msg")
      }
    }), akkaUri.actor)

    log.info(s"listening: ${sourceActor}...")

    // Source.queue[ByteString](bufferSize, overflowStrategy)
    //   .mapMaterializedValue { queue =>
    //     sourceActor ! queue
    //     sourceActor
    //   }
    
    // Source.actorRef[ByteString](
    //   bufferSize = bufferSize,
    //   overflowStrategy = overflowStrategy
    // )
    // .mapMaterializedValue { actorRef =>
    //   actorRef
    // }
    source
  }

  // ----------------------------------------------------------------------------------------------------------
  def toAkka[T <: Ingestable](uri:String,format:String)(implicit fmt:JsonFormat[T],as:ActorSystem) = {
    import akka.event.Logging
    import spray.json._

    val random = new Random()
    val akkaUri = AkkaURI(uri)

    val config = ConfigFactory.parseString(s"""
      akka {
        actor {
          provider = remote
        }
        remote {
          artery {
            enabled = on
            transport = tcp
            canonical.hostname = "${akkaUri.host.getOrElse("127.0.0.1")}"
            canonical.port = ${random.nextInt(65000 - 1024 + 1) + 1024}
          }
        }
      }
    """)

    val system = if(akkaUri.port.isDefined) 
      ActorSystem(akkaUri.system,config)
    else
      as
    
    val actorSelectionRemote = system.actorSelection(uri)
    
    log.info(s"--> ${actorSelectionRemote}")

    Flow[T]
      .map { t =>
        val message = formatter(t, format)
        log.info(s"${message} => ${actorSelectionRemote.anchorPath}${actorSelectionRemote.pathString}")
        actorSelectionRemote ! message
        t
      }
      .toMat(Sink.ignore)(Keep.right)
  }

  // ----------------------------------------------------------------------------------------------------------
  def toFile[T <: Ingestable](file:String,format:String)(implicit fmt:JsonFormat[T]) = {
    import akka.event.Logging
    import spray.json._

    if(file.trim.isEmpty) 
      Sink.ignore 
    else {
      //toSinkRestart({
        Flow[T]
          // .map(t => if(file.endsWith(".json")) 
          //     s"${t.toJson}\n" 
          //   else if(file.endsWith(".csv")) 
          //     s"${t.toCSV}\n" 
          //   else 
          //     s"${t.toLog}\n"
          // )
          .map(t => formatter(t,format))
          .map(ByteString(_))          
          .log(s"${this}")
          .withAttributes(Attributes.createLogLevels(Logging.DebugLevel, Logging.InfoLevel, Logging.ErrorLevel))       
          .toMat(FileIO.toPath(
            Paths.get(Util.pathToFullPath(Util.toFileWithTime(file))),options =  Set(WRITE, CREATE))
          )(Keep.right)
      //})
    }
  }

  def toHTTP[T <: Ingestable](uri:String,format:String = "json")(implicit as:ActorSystem,fmt:JsonFormat[T]) = {
    
    val httpUri = skel.uri.HttpURI(uri)

    val sink = 
    if(httpUri.uri.trim.isEmpty) {
      log.warn(s"invalid uri: ${uri}")
      Sink.ignore 
    } else {     
      import spray.json._
     
      def retry_deterministic(as:ActorSystem,timeout:FiniteDuration) = ConnectionPoolSettings(as)
        .withBaseConnectionBackoff(timeout)
        .withMaxConnectionBackoff(timeout)
    
      Flow[T]
        .map(t => {
          // val j = t.toJson          
          // if(pretty) j.prettyPrint else j.compactPrint
          formatter(t,format).utf8String
        })
        .mapAsync(1)(body => {
          log.debug(s"body(${body}) --> ${httpUri.uri}")
          
          val http =
            Http()
            .singleRequest(
              HttpRequest(
                HttpMethod.custom(httpUri.verb),
                uri = httpUri.uri, 
                entity = HttpEntity(ContentTypes.`application/json`,body),
                headers = httpUri.headers.map{case (k,v) => RawHeader(k,v)}.toSeq
              ),
              //
              settings = retry_deterministic(as,FiniteDuration(1000L,TimeUnit.MILLISECONDS))
            )          
          
          val f = http.flatMap( r => r match {
            case response @ HttpResponse(status, _ , entity, _) =>
              status match {
                case StatusCodes.OK => 
                  val data = entity.dataBytes.runReduce(_ ++ _)
                  val body = data.map(_.utf8String)                  
                  body
                case _ =>
                  log.error(s"error: ${status}")
                  val body = entity.dataBytes.runReduce(_ ++ _)
                  val txt = Await.result(body.map(_.utf8String),FiniteDuration(5000L,TimeUnit.MILLISECONDS))
                  throw new Exception(s"${status}: ${txt}")
              }
            case f   => 
              log.error(s"failed connection: ${httpUri.uri}",f)
              throw new Exception(s"${f}")
          })
          
          f
        })
        .log(s"-> HTTP(${httpUri.uri})")
        .map(r => {
          log.debug(s"rsp: '${r}'")
          r
        })
        .toMat(
          Sink.ignore
        )(Keep.right)
    }

    RestartSink.withBackoff[T](retrySettingsDefault) { () =>
      log.info(s"Connection --> ${httpUri.uri}")
      sink
    }
  }

  // ===== WebSocket Server Sink =========================================================================
  def toWebsocketServer[T <: Ingestable](uri:String,format:String="", buffer:Int=10000, timeout:Long = 1000L*60*60*24)(implicit as:ActorSystem,fmt:JsonFormat[T]) = { 
    import io.syspulse.skel.service.ws._
    import akka.actor.typed.scaladsl.ActorContext

    class WsProxyServer(idleTimeout:Long,uri:String = "ws") extends WebSocket(idleTimeout) {
      // ignore incoming messages
      override def process(m:Message,a:ActorRef):Message = m        

      val routes: Route =
        pathPrefix(uri) { 
          extractClientIP { addr => {
            log.info(s"<-- ws://${addr}")
            
            pathPrefix(Segment) { topic =>
              handleWebSocketMessages(this.listen(topic))
            } ~
            pathEndOrSingleSlash {
              handleWebSocketMessages(this.listen())
            }
          }}
      }
      
      def broadcast(msg:String) = {
        broadcastText(msg)
      }            
    }
    
    if(uri.trim.isEmpty) {
      log.warn(s"invalid uri: ${uri}")
      Sink.ignore 
    } else {     
      import spray.json._
      import akka.http.scaladsl.model.{ HttpResponse, Uri, HttpRequest }
      import akka.http.scaladsl.model.HttpMethods._
      import akka.http.scaladsl.model.AttributeKeys
      import akka.event.Logging
     
      def retry_deterministic(as:ActorSystem,timeout:FiniteDuration) = ConnectionPoolSettings(as)
        .withBaseConnectionBackoff(timeout)
        .withMaxConnectionBackoff(timeout)

      val (host,port,suffix) = uriToHostPort(uri)
    
      // val (actor,source0) = Source
      //   .actorRef[String](buffer,OverflowStrategy.fail)
      //   .preMaterialize()   
      
      val ws = new WsProxyServer(timeout,uri = suffix)
      log.info(s"Listen: ws://${host}:${port}/${suffix} ...")      
      val bindingFuture = Http().newServerAt(host, port).bind(ws.routes)      

      val sink0 = Flow[T]
        .map(t => { 
          val out = formatter(t,format).utf8String
          // format match {
          //   case "json" => t.toJson.compactPrint
          //   case "csv" => t.toCSV
          //   case _ => t.toLog
          // }
          
          //log.debug(s"out=${out}")
          ws.broadcast(out)
        }) 
        .toMat(
          Sink.ignore
        )(Keep.right)

      val sink = sink0
      
      sink
    }
  }  

  // ===== WebSocket Client Sink =========================================================================

  def toWebsocket[T <: Ingestable](uri: String, format: String = "", buffer: Int = 10000, timeout: Long = 1000L * 60 * 60 * 24, retrySettings: RestartSettings = retrySettingsDefault)
                                  (implicit as: ActorSystem, fmt: JsonFormat[T]) = {

    if(uri.trim.isEmpty) {
      log.warn(s"invalid uri: ${uri}")
      Sink.ignore 
    } else {     
            
      val webSocketActor = as.actorOf(WebSocketActor.props(uri, format, buffer, timeout, retrySettings))
      
      Sink.foreach[T] { message =>
        webSocketActor ! WebSocketActor.SendMessage(message)
      }
    }
  }
  
  // WebSocket Actor that handles connection and reconnection
  object WebSocketActor {
    case class SendMessage[T <: Ingestable](message: T)
    case object Connect
    case object Reconnect
    case object ConnectionEstablished
    case class ConnectionFailed(reason: Throwable)
    case object ConnectionClosed
    
    def props[T <: Ingestable](uri: String, format: String, buffer: Int, timeout: Long, retrySettings: RestartSettings)(implicit fmt: JsonFormat[T]): Props = 
      Props(new WebSocketActor(uri, format, buffer, timeout, retrySettings))
  }
  
  class WebSocketActor[T <: Ingestable](uri: String, format: String, buffer: Int, timeout: Long, retrySettings: RestartSettings)(implicit fmt: JsonFormat[T]) extends Actor {
    import WebSocketActor._
    import context.dispatcher
    import akka.stream.Materializer
    import akka.pattern.after
    
    implicit val materializer: Materializer = Materializer(context.system)
    
    val log = Logger(this.getClass)
    
    private var messageQueue: Vector[T] = Vector.empty
    private var isConnected = false
    private var reconnectTimer: Option[Cancellable] = None
    private var healthCheckTimer: Option[Cancellable] = None
    private var messageQueueActor: Option[SourceQueueWithComplete[Message]] = None
    private var reconnectAttempts = 0
    
    override def preStart(): Unit = {
      log.info(s"WebSocket Actor starting for: ${uri}")
      self ! Connect
    }
    
    override def receive: Receive = {
      case SendMessage(message) =>
        message match {
          case msg: T =>
            if (isConnected && messageQueueActor.isDefined) {
              sendMessageToQueue(msg, messageQueueActor.get)
            } else {
              messageQueue = messageQueue :+ msg
              if (messageQueue.length > buffer) {
                messageQueue = messageQueue.drop(1)
                log.warn(s"Message queue full: ${uri}")
              }
            }
          case _ =>
            log.error(s"Invalid message type received: ${uri}")
        }
        
      case Connect =>
        log.info(s"Connecting --> ${uri}")
        connect()
        
      case Reconnect =>
        log.info(s"Reconnecting --> ${uri}")
        connect()
        
      case ConnectionEstablished =>
        log.info(s"Connected: ${uri}")
        isConnected = true
        resetReconnectAttempts() // Reset backoff counter on successful connection
        messageQueue.foreach(msg => sendMessageToQueue(msg, messageQueueActor.get))
        messageQueue = Vector.empty
        
      case ConnectionFailed(reason) =>
        //log.debug(s"Connection failed: ${uri}: ${reason.getMessage}")
        isConnected = false
        messageQueueActor = None
        scheduleReconnect()
        
      case ConnectionClosed =>
        //log.debug(s"Connection closed: ${uri}")
        isConnected = false
        messageQueueActor = None
        scheduleReconnect()
    }
    
    private def connect(): Unit = {
      try {
        // Create a queue for outgoing messages
        val (queue, source) = Source.queue[Message](buffer, akka.stream.OverflowStrategy.backpressure)
          .preMaterialize()
        
        val flow: Flow[Message, Message, Future[Done]] =
          Flow.fromSinkAndSourceMat(Sink.ignore, source)(Keep.left)

        val (upgradeResponse, connectionClosed) = Http(context.system)
          .singleWebSocketRequest(
            WebSocketRequest(uri),
            flow
            // Flow.fromSinkAndSource(
            //   Sink.ignore,
            //   source       // Send messages from the queue
            // )
          )
        
        upgradeResponse.onComplete {
          case Success(upgrade) =>
            if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
              log.info(s"Connected: ${uri}: ${upgrade.response.status}")
              isConnected = true
              messageQueueActor = Some(queue)
              // Send queued messages
              messageQueue.foreach(msg => sendMessageToQueue(msg, queue))
              messageQueue = Vector.empty
            } else {
              self ! ConnectionFailed(new Exception(s"Connection failed: ${uri}: ${upgrade.response.status}"))
            }
          case Failure(e) =>
            //log.debug(s"Connection failed: ${uri}: ${e.getMessage}: ${if(e.getCause != null) e.getCause.getMessage else ""}")
            self ! ConnectionFailed(e)
        }
        
        // Connection closure will be detected by health check and queue failures
        connectionClosed.onComplete {
          case Success(_) =>
            log.info(s"Connection closed: ${uri}")
            self ! ConnectionClosed
          case Failure(e) =>
            log.error(s"Connection failed: ${uri}: ${e.getMessage}: ${if(e.getCause != null) e.getCause.getMessage else ""}")
            self ! ConnectionClosed
        }
        
      } catch {
        case e: Exception =>
          log.error(s"Connection failed: ${uri}: ${e.getMessage}: ${if(e.getCause != null) e.getCause.getMessage else ""}")
          self ! ConnectionFailed(e)
      }
    }
    
    private def sendMessageToQueue(message: T, queue: SourceQueueWithComplete[Message]): Unit = {
      try {
        val body = formatter(message, format).utf8String
        val wsMessage = TextMessage(body)
        
        queue.offer(wsMessage).onComplete {
          case Success(QueueOfferResult.Enqueued) =>
            log.debug(s"Message queued: ${body.take(50)}...")
          case Success(QueueOfferResult.Dropped) =>
            log.warn(s"Message dropped: ${uri}")
          case Success(QueueOfferResult.Failure(ex)) =>
            log.error(s"Failed to queue message: ${uri}", ex)
            self ! ConnectionFailed(ex)
          case Success(QueueOfferResult.QueueClosed) =>
            log.error(s"Queue closed: ${uri}")
            self ! ConnectionClosed
          case Failure(ex) =>
            log.error(s"Failed to queue message: ${uri}", ex)
            self ! ConnectionFailed(ex)
        }
        
      } catch {
        case e: Exception =>
          log.warn(s"Failed to format message: ${uri}", e)
          // self ! ConnectionFailed(e)
      }
    }
    
    private def scheduleReconnect(): Unit = {
      reconnectTimer.foreach(_.cancel())
      
      reconnectAttempts += 1
      
      // Calculate exponential backoff with jitter
      val backoff = calculateBackoff(reconnectAttempts)
      
      log.debug(s"Scheduling Reconnect [${reconnectAttempts},${backoff}]: ${uri}")
      
      reconnectTimer = Some(
        context.system.scheduler.scheduleOnce(
          backoff,
          self,
          Reconnect
        )
      )
    }
    
    private def calculateBackoff(attempt: Int): FiniteDuration = {
      import scala.util.Random
      
      // Exponential backoff: minBackoff * (2 ^ attempt)
      val exponentialDelay = retrySettings.minBackoff.toMillis * math.pow(2, attempt - 1)
      
      // Cap at maxBackoff
      val cappedDelay = math.min(exponentialDelay, retrySettings.maxBackoff.toMillis)
      
      // Add jitter (±25% random variation)
      val jitter = Random.nextDouble() * 0.5 + 0.75 // 0.75 to 1.25 multiplier
      val jitteredDelay = cappedDelay * jitter
      
      FiniteDuration(jitteredDelay.toLong, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    private def resetReconnectAttempts(): Unit = {
      reconnectAttempts = 0
    }
    
    override def postStop(): Unit = {
      reconnectTimer.foreach(_.cancel())
      messageQueueActor.foreach(_.complete())
    }
  }

  // Hive Rotators
  abstract class Rotator {
    def init(file:String,fileLimit:Long,fileSize:Long):Unit
    def isRotatable():Boolean
    def needRotate(count:Long,size:Long):Boolean
    def rotate(file:String,count:Long,size:Long):Option[String]
  }

  class RotatorCurrentTime extends Rotator {
    var tsRotatable = false
    var nextTs = 0L

    def init(file:String,fileLimit:Long,fileSize:Long) = {      
      //tsRotatable = Util.extractDirWithSlash(file).matches(""".*[{}].*""")
      tsRotatable = file.matches(""".*[{}].*""")
    }

    def isRotatable():Boolean = tsRotatable
    
    def needRotate(count:Long,size:Long):Boolean = {            
      isRotatable() && (nextTs != 0 && System.currentTimeMillis() >= nextTs)      
    }

    def rotate(file:String,count:Long,size:Long):Option[String]  = {
      nextTs = if(tsRotatable) Util.nextTimestampFile(file) else 0L
      val now = System.currentTimeMillis()
      Some(Util.pathToFullPath(Util.toFileWithTime(file,now)))
    }
  }

  // supply custom Timestamp
  class RotatorTimestamp(askTime:()=>Long) extends RotatorCurrentTime {
  
    override def needRotate(count:Long,size:Long):Boolean = {      
      isRotatable() && (nextTs != 0L && askTime() >= nextTs)
    }

    override def rotate(file:String,count:Long,size:Long):Option[String]  = {
      val ts = askTime()
      nextTs = if(tsRotatable) 
        //ts 
        Util.nextTimestampFile(file,ts)
      else 
        0L
      Some(Util.pathToFullPath(Util.toFileWithTime(file,ts)))
    }
  }


  def toFileNew[T <: Ingestable](file:String,rotator:(T,String) => String,format:String)(implicit mat: Materializer,fmt:JsonFormat[T]) = {
    import spray.json._  
    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[T].map( t => {
        // val out = if(file.endsWith(".json")) 
        //       s"${t.toJson}\n" 
        //     else if(file.endsWith(".csv")) 
        //       s"${t.toCSV}\n" 
        //     else 
        //       s"${t.toLog}\n"
        val out = formatter(t,format)
        Source
          .single(ByteString(out))          
          .toMat(FileIO.toPath(
              { 
                val path = rotator(t,file)
                val p = Paths.get(path)
                val dir = p.getParent()
                if(!Files.exists(dir)) {
                  try {
                    Files.createDirectories(dir)
                  } catch {
                    case e:Exception => {
                      log.warn(s"Failed to create dir: '${path}': ${e.getMessage}")                
                    }
                  }
                }
                p
              },
              options =  Set(WRITE, CREATE)
            )
          )(Keep.left).run()

      })
      .toMat(Sink.seq)(Keep.both)
  }

  def toHive(file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit rotator:Rotator) = {
    
    val fileRotateTrigger: () => ByteString => Option[Path] = () => {
      var currentFilename: Option[String] = None
      var inited = false
      var count: Long = 0L
      var size: Long = 0L
      
      rotator.init(file,fileLimit,fileSize)
          
      (element: ByteString) => {
        if(inited && !rotator.needRotate(count,size) && count < fileLimit && size < fileSize) {
          
          count = count + 1
          size = size + element.size
          None
        } else {          
          
          currentFilename = rotator.rotate(file,count,size)
          
          log.info(s"count=${count},size=${size},limits=(${fileLimit},${fileSize}) => ${currentFilename}")
          
          count = 0L
          size = 0L
          inited = true          
          
          val currentDirname = Util.extractDirWithSlash(currentFilename.get)
          try {
            // try to create dir
            val p = Files.createDirectories(Path.of(currentDirname))
            log.info(s"created dir: ${p}")
            val outputPath = currentFilename.get
            Some(Paths.get(outputPath))

          } catch {
            case e:Exception => None
          }
        }
      }
    }

    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[Ingestable]
        .map(t=>s"${t.toLog}\n")
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger))(Keep.right)
  }

  def toHiveFileSize[T <: Ingestable](file:String,format:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit fmt:JsonFormat[T]) = {
    import spray.json._
    val fileRotateTrigger: () => ByteString => Option[Path] = () => {
      var currentFilename: Option[String] = None
      var inited = false
      var count: Long = 0L
      var size: Long = 0L
      var currentTs = System.currentTimeMillis
      (element: ByteString) => {
        if(inited && (count < fileLimit && size < fileSize)) {
          count = count + 1
          size = size + element.size
          None
        } else {
          currentFilename = Some(Util.pathToFullPath(Util.toFileWithTime(file,currentTs)))
          
          count = 0L
          size = 0L
          inited = true
          
          val currentDirname = Util.extractDirWithSlash(currentFilename.get)
          try {
            // try to create dir
            Files.createDirectories(Path.of(currentDirname))
            val outputPath = currentFilename.get
            Some(Paths.get(outputPath))

          } catch {
            case e:Exception => None
          }                    
        }
      }
    }

    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[T]
        .map(t => {
          // if(file.endsWith(".json")) 
          //     s"${t.toJson}\n" 
          //   else if(file.endsWith(".csv")) 
          //     s"${t.toCSV}\n" 
          //   else 
          //     s"${t.toLog}\n"
          formatter(t,format)
        })
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger))(Keep.right)
  }

  // S3 mounted as FileSystem/Volume
  // Does not support APPEND 
  def toFS3[T <: Ingestable](file:String,format:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit rotator:Rotator,fmt:JsonFormat[T]) = {
    import spray.json._
    val log = Logger(s"${this}")

    val fileRotateTrigger: () => ByteString => Option[Path] = () => {
      var currentFilename: Option[String] = None
      var inited = false
      var count: Long = 0L
      var size: Long = 0L

      rotator.init(file,fileLimit,fileSize)
          
      (element: ByteString) => {
        
        if(inited && (
            count < fileLimit && 
            size < fileSize && 
            ! rotator.needRotate(count,size)
          )
        ) {
          
          count = count + 1
          size = size + element.size
          
          None
        } else {
          currentFilename = rotator.rotate(file,count,size)
          
          log.info(s"count=${count},size=${size},limits=(${fileLimit},${fileSize}) => ${currentFilename}")

          count = 0L
          size = 0L
          inited = true
          
          val currentDirname = Util.extractDirWithSlash(currentFilename.get)          
          try {
            // try to create dir
            val p = Files.createDirectories(Path.of(currentDirname))
            log.info(s"created dir: ${p}")
            val outputPath = currentFilename.get
            Some(Paths.get(outputPath))

          } catch {
            case e:Exception => {
              log.error(s"Coould not create dir: ${currentDirname}",e)
              None
            }
          }                    
        }
      }
    }

    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[T]
        .map(t => {
          // if(file.endsWith(".json")) 
          //     s"${t.toJson}\n" 
          //   else if(file.endsWith(".csv")) 
          //     s"${t.toCSV}\n" 
          //   else 
          //     s"${t.toLog}\n"
          formatter(t,format)
        })
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger,fileOpenOptions = Set(StandardOpenOption.CREATE,StandardOpenOption.WRITE)))(Keep.right)
  }

  // Parquet (does not support !)
  // ATTENTION: this is a very hacky implementation
  // parquet4s supports its own Rotator, but cannot change file name to be compatible with fs3://
  def toParq[T](fileUri:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)
    (implicit rotator:Rotator,parqEncoders:ParquetRecordEncoder[T],parsResolver:ParquetSchemaResolver[T]) = {
    
    val log = Logger(s"${this}")
    val parqUri = skel.uri.ParqURI(fileUri)
    val file = parqUri.file
    val writeMode = parqUri.mode match {
      case "OVERWRITE" => Mode.OVERWRITE
      case "CREATE" => Mode.CREATE
    }
    val zip = parqUri.zip match {
      case "parq" => CompressionCodecName.UNCOMPRESSED
      case "snappy" => CompressionCodecName.SNAPPY
      case "gzip" => CompressionCodecName.GZIP
      case "lz4" => CompressionCodecName.LZ4
      case "zstd" => CompressionCodecName.ZSTD
      case _ => CompressionCodecName.UNCOMPRESSED
    }
    val parqOptions = ParquetWriter.Options(
      writeMode = writeMode,
      compressionCodecName = zip,
    )
    
    val fileRotateTrigger: () => T => Option[Sink[T,Future[Done]]] = () => {
      var currentFilename: Option[String] = None
      var inited = false
      var count: Long = 0L
      var size: Long = 0L
      
      rotator.init(file,fileLimit,fileSize)
          
      (element: T) => {
        
        if(inited && (
            count < fileLimit && 
            size < fileSize && 
            ! rotator.needRotate(count,size)
          )
        ) {
          
          count = count + 1
          size = size + 0//element.size
          
          None
        } else {          
          currentFilename = rotator.rotate(file,count,size)
          
          log.info(s"count=${count},size=${size},limits=(${fileLimit},${fileSize}) => ${currentFilename}")

          count = 0L
          size = 0L
          inited = true
          
          val currentDirname = Util.extractDirWithSlash(currentFilename.get)          
          try {
            // try to create dir
            val p = Files.createDirectories(Path.of(currentDirname))
            log.info(s"created dir: ${p}")
            val outputPath = currentFilename.get

            val parq = ParquetStreams
              .toParquetSingleFile.of[T]
              .options(parqOptions)
              .write(com.github.mjakubowski84.parquet4s.Path(outputPath))
              //ParquetWriter.of[T].options(parqOptions).build(com.github.mjakubowski84.parquet4s.Path(outputPath))
            Some(parq)

          } catch {
            case e:Exception => {
              log.error(s"Coould not create dir: ${currentDirname}",e)
              None
            }
          }                    
        }
      }
    }

    if(file.trim.isEmpty) 
      Sink.ignore 
    else {
      val fileOpenOptions = Set(StandardOpenOption.CREATE,StandardOpenOption.WRITE)
      val sinkParq = LogRotatorSink.withTypedSinkFactory[T, Sink[T,Future[Done]], Done](fileRotateTrigger,sinkFactory = (s) => s)
      Flow[T]
        .toMat(sinkParq)(Keep.right)
    }
  }

  def toElastic[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {
    val es = new ToElastic[T](uri)(fmt)
    Flow[T]
      .mapConcat(t => es.transform(t))
      .toMat(es.sink())(Keep.right)
  }

  def toKafka[T <: Ingestable](uri:String,format:String = "")(fmt:JsonFormat[T]) = {
    val kafka = new ToKafka[T](uri,format)(fmt)
    
    val kafkaSink = kafka.sink()
    val sink = RestartSink.withBackoff[T](retrySettingsDefault) { () =>
      log.info(s"Restarting -> Kafka(${uri})...")
      kafkaSink
    }
    
    Flow[T]
      .toMat(sink)(Keep.right)
  }

  def fromKafka[T <: Ingestable](uri:String) = {
    val kafka = new FromKafka[T](uri)
    kafka.source()
  }
  
  def toJson[T <: Ingestable](uri:String,flush:Boolean = false,pretty:Boolean = false)(implicit fmt:JsonFormat[T]) = {
    import spray.json._
    Flow[T]
      .map(o => if(o!=null) {
        val j = o.toJson
        val js = if(pretty) j.prettyPrint else j
        ByteString(s"${js}\n")
      } else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  }

  def toCsv[T <: Ingestable](uri:String,flush:Boolean = false):Sink[T, Future[IOResult]] = {
    Flow[T]
      .map(o => if(o!=null) ByteString(o.toCSV+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  }

  def toLog[T <: Ingestable](uri:String,flush:Boolean = false):Sink[T, Future[IOResult]] = {
    Flow[T]
      .map(o => if(o!=null) ByteString(o.toLog+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  }

  def toStdout[O <: Ingestable](flush:Boolean = false,format:String="")(implicit fmt:JsonFormat[O]): Sink[O, Future[IOResult]] = toPipe(flush,format,System.out)(fmt)
  def toStderr[O <: Ingestable](flush:Boolean = false,format:String="")(implicit fmt:JsonFormat[O]): Sink[O, Future[IOResult]] = toPipe(flush,format,System.err)(fmt)
  def toPipe[O <: Ingestable](flush:Boolean,format:String,pipe:java.io.PrintStream)(implicit fmt:JsonFormat[O]): Sink[O, Future[IOResult]] = {
    import spray.json._
    Flow[O]
      //.map(o => if(o!=null) ByteString(o.toString+"\n") else ByteString())
      .map(o => this.formatter(o,format,"\n"))
      .toMat(StreamConverters.fromOutputStream(() => pipe,flush))(Keep.right)  
  }
  
  // This sink returns Future[Done] and not possible to wait for completion of the flow
  def toOut() = Sink.foreach(println _)
  def toErr() = Sink.foreach{o:Any => Console.err.println(o)}

  // JDBC
  def toJDBC[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {     
    val jdbc = new ToJDBC[T](uri,None).flow()
    
    Flow[T]
      .via(jdbc)
      .log("jdbc")
      .toMat(Sink.ignore)(Keep.right)
  }

  def toRedis[T <: Ingestable](uri:String,format:String = "")(fmt:JsonFormat[T],as:ActorSystem) = {
    val redis = new ToRedis[T](uri,format)(fmt,as)    
    val sink = RestartSink.withBackoff[T](retrySettingsDefault) { () =>
      log.info(s"Restarting -> Redis(${uri})...")
      Sink.ignore
    }
    
    redis.sink
      .toMat(sink)(Keep.right)
  }

  def fromRedis[T <: Ingestable](uri:String,format:String = "")(fmt:JsonFormat[T],as:ActorSystem) = {    
    //val redis = new FromRedis[T](uri,format)(fmt,as)

    val source = RestartSource.withBackoff[ByteString](retrySettingsDefault) { () =>
      log.info(s"Restarting -> Redis(${uri})...")
      val redis = new FromRedis[T](uri,format)(fmt,as)      
      redis.source      
    }

    source
  }

// ====================================================================================================================================
// ====================================================================================================================================
// ====================================================================================================================================
  class RedisClient(uri:String)(implicit as:ActorSystem) {    
    val redisUri = RedisURI(uri)
    val timeout = FiniteDuration(redisUri.timeout,TimeUnit.MILLISECONDS)

    // import scredis.PubSubMessage
    protected def subscriptionHandler: scredis.Subscription = {
      case m: PubSubMessage.Subscribe => log.debug(s"Subscribed: ${m.channel}")
      case m: PubSubMessage.Message => log.debug(s"Received: ${m.channel}: ${m.readAs[String]()}")
      case m: PubSubMessage.Unsubscribe => log.debug(s"Unsubscribed: ${m.channelOpt}")
      case m: PubSubMessage.PSubscribe => log.debug(s"Subscribed: ${m.pattern}")
      case m: PubSubMessage.PMessage => log.debug(s"Received: ${m.channel}: ${m.pattern}: ${m.readAs[String]()}")
      case m: PubSubMessage.PUnsubscribe => log.debug(s"Unsubscribed: ${m.patternOpt}")
      case e: PubSubMessage.Error => log.warn(s"${e}")
    }
      
    // Generate unique name for this Redis client instance to avoid conflicts during reconnection
    val clientName = s"redis-${redisUri.host}-${redisUri.port}-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString.take(8)}"
    
    val redis = if(redisUri.channel.isDefined) {
      val redis = Redis.withActorSystem(
        host = redisUri.host,
        port = redisUri.port,
        authOpt = redisUri.pass match {
          case None => None
          case Some(u) => Some(AuthConfig(username = redisUri.user, password = redisUri.pass.getOrElse("")))
        },
        database = redisUri.db,
        connectTimeout = timeout,
        nameOpt = Some(clientName),
        subscription = subscriptionHandler//RedisConfigDefaults.LoggingSubscription
      )(as)      
      redis
    } else {
      Redis(
        host = redisUri.host,
        port = redisUri.port,
        authOpt = redisUri.pass match {
          case None => None
          case Some(u) => Some(AuthConfig(username = redisUri.user, password = redisUri.pass.getOrElse("")))
        },
        database = redisUri.db,
        connectTimeout = timeout,
        nameOpt = Some(clientName)
     )
    }

    def push(key:String,value:ByteString):Future[Boolean] = {
      redisUri.channel match {
        case Some(channel) => redis.publish(channel,value.utf8String).map(_ => true)
        case None => redis.set(key,value.utf8String)
      }
    }
    
    // Import internal ActorSystem's dispatcher (execution context) to register callbacks
    //import redis.dispatcher

    def client() = redis
  }

  class ToRedis[T <: Ingestable](uri:String,format:String)(implicit jf:JsonFormat[T],as:ActorSystem) extends RedisClient(uri)(as) {    
    val formatOutput = if(!format.isBlank) format else "json"
    
    def transform(t:T):ByteString = {
      formatter(t,formatOutput)
    }

    def sink = Flow[T]
      .mapAsync(1)(t => {
        val key = t.getKey.map(_.toString).getOrElse(t.hashCode().toString)
        val o = this.transform(t)
        val f = this.push(key,o).map(_ => t)        
        f
      })
      .log("redis")
  }

  // from Redis supports only PubSub subscription
  class FromRedis[T <: Ingestable](uri:String,format:String)(implicit jf:JsonFormat[T],as:ActorSystem) extends RedisClient(uri)(as) {        
    val formatOutput = if(!format.isBlank) format else "json"

    // Create a queue-based source for receiving messages from subscription
    val (messageQueue, messageSource) = Source
      .queue[ByteString](bufferSize = redisUri.ops.get("buffer").map(_.toInt).getOrElse(1000), OverflowStrategy.backpressure)
      .preMaterialize()

    override protected def subscriptionHandler: scredis.Subscription = {
      case m: PubSubMessage.Subscribe => 
        log.info(s"Subscribed: ${m.channel}")
      case m: PubSubMessage.Message => 
        val data = ByteString(m.readAs[String]())
        messageQueue.offer(data).onComplete {
          case Success(_) => log.debug(s"${data} -> ${m.channel}")
          case Failure(e) => 
            log.error(s"Failed to publish: ${m.channel}: ${e.getMessage}")
            // Queue failure indicates source is closed, fail it to trigger restart
            messageQueue.fail(new Exception(s"Queue offer failed: ${e.getMessage}", e))
        }
      case m: PubSubMessage.Unsubscribe => 
        log.warn(s"Unexpected unsubscribe: ${m.channelOpt} - connection may be lost")
        // Unexpected unsubscribe indicates connection loss, fail queue to trigger restart
        messageQueue.fail(new Exception(s"Unexpected unsubscribe from channel: ${m.channelOpt}"))
      case m: PubSubMessage.PSubscribe => 
        log.debug(s"Subscribed: ${m.pattern}")
      case m: PubSubMessage.PMessage => 
        val data = ByteString(m.readAs[String]())
        messageQueue.offer(data).onComplete {
          case Success(_) => log.debug(s"${data} -> ${m.channel} (${m.pattern})")
          case Failure(e) => 
            log.error(s"Failed to publish: ${m.channel}: ${e.getMessage}")
            messageQueue.fail(new Exception(s"Queue offer failed: ${e.getMessage}", e))
        }
      case m: PubSubMessage.PUnsubscribe => 
        log.warn(s"Unexpected unsubscribe: ${m.patternOpt} - connection may be lost")
        messageQueue.fail(new Exception(s"Unexpected unsubscribe from pattern: ${m.patternOpt}"))
      case e: PubSubMessage.Error => 
        log.error(s"PubSub error: ${e} - failing source to trigger restart")
        // Connection error - fail the queue to trigger RestartSource
        messageQueue.fail(new Exception(s"Redis PubSub error: ${e}"))

      case e =>
        log.error(s"Unexpected PubSub message",e)
        messageQueue.fail(new Exception(s"Redis PubSub error: ${e}"))
    }

    def transform(t:T):ByteString = {
      formatter(t,formatOutput)
    }

    def source: Source[ByteString, _] = {
      if(!redisUri.channel.isDefined) {
        throw new Exception(s"Channel undefined: ${uri}")
      }

      // Source.futureSource(
      //   redis.subscriber.subscribe(redisUri.channel.get).map { _ =>
      //     log.info(s"Subscribed to: ${redisUri.channel.get}")
      //     messageSource            
      //   }.recover {
      //     case e: Exception =>
      //       log.error(s"Failed to subscribe to Redis: ${e.getMessage}")
      //       Source.failed(e)
      //   }
      // )
      
      // Subscribe asynchronously - if it fails, the source will fail
      redis.subscriber.subscribe(redisUri.channel.get).onComplete {
        case Success(_) => 
          log.info(s"Subscribed to: ${redisUri.channel.get}")
        case Failure(e) => 
          log.error(s"Failed to subscribe to Redis: ${e.getMessage}")
          // Fail the queue to trigger source failure and restart
          messageQueue.fail(new Exception(s"Failed to subscribe: ${e.getMessage}", e))
      }

      // Add health check - periodically ping Redis to detect connection loss
      // Merge health check failures with message source
      val healthCheckInterval = FiniteDuration(redisUri.ops.get("healthCheck").map(_.toLong).getOrElse(5000L), TimeUnit.MILLISECONDS)
      val healthCheckSource = Source
        .tick(healthCheckInterval, healthCheckInterval, ())
        .mapAsync(1) { _ =>
          redis.ping().map(_ => ByteString.empty).recover {
            case e: Exception =>
              log.error(s"Redis health check failed: ${e.getMessage} - failing source to trigger restart")
              messageQueue.fail(new Exception(s"Redis connection lost: ${e.getMessage}", e))
              throw e
          }
        }
        .filter(_.nonEmpty) // Filter out empty health check messages

      // Merge message source with health check - if health check fails, source fails
      Source.combine(messageSource, healthCheckSource)(Merge(_))
        .log("redis")
    }
      
  }

  // === JDBC ============================================================================================  
  class ToJDBC[T <: Ingestable](dbUri:String,configuration:Option[Configuration]=None) 
    extends skel.store.StoreDBCore(dbUri,"",None) {
    
    val ctx = dbType match {
      case "mysql" => 
        new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig)) //with Queries
      case "postgres" => 
        new PostgresJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig)) //with Queries      
      case _ => 
        new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig)) //with Queries
    }

    import ctx._
          
    def flow() = Flow[T].map( t => {
      log.debug(s"INSERT: ${t}")

      val SQL_DATA = Util.traverseAnySQL(t).map( kv => kv._2).mkString(",")

      val SQL = s"INSERT INTO ${t.getClass().getSimpleName()} VALUES(${SQL_DATA})"

      log.debug(s"SQL='${SQL}'")

      try {
        //ctx.insertOnly(t)
        val r = ctx.executeAction(SQL)(ExecutionInfo.unknown, ())
        t
      } catch {
        case e:Exception => 
          throw new Exception(s"could not insert: ${e}")
      }
    })
  }

  // === Kafka ================================================================================================

  // Kafka Client Flows 
  class ToKafka[T <: Ingestable](uri:String,format:String)(implicit jf:JsonFormat[T]) extends skel.ingest.kafka.KafkaSink[T] {
    import spray.json._
    
    val kafkaUri = KafkaURI(uri)
    val formatOutput = if(!format.isBlank) format else if(kafkaUri.isRaw) "raw" else "json"
    
    val sink0 = sink(kafkaUri.broker,Set(kafkaUri.topic),ops = kafkaUri.ops)

    def sink():Sink[T,_] = sink0
    
    override def transform(t:T):ByteString = {
      // val o = if(kafkaUri.isRaw) 
      //   ByteString(t.toString)
      // else
      //   ByteString(t.toJson.compactPrint)
      formatter(t,formatOutput)
    }  
  }

  class FromKafka[T <: Ingestable](uri:String) extends skel.ingest.kafka.KafkaSource[T] {
    val kafkaUri = KafkaURI(uri)
      
    def source():Source[ByteString,_] = source(
      kafkaUri.broker,
      Set(kafkaUri.topic),
      kafkaUri.group,
      offset = kafkaUri.offset,
      ops = kafkaUri.ops
    )
  }
  
  // Elastic Client Flow
  class ToElastic[T <: Ingestable](uri:String)(jf:JsonFormat[T]) extends ElasticClient[T] {
    val elasticUri = ElasticURI(uri)
    connect(elasticUri.url,elasticUri.index)

    override implicit val fmt:JsonFormat[T] = jf

    def sink():Sink[WriteMessage[T,NotUsed],Future[Done]] = 
      ElasticsearchSink.create[T](
        ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
      )(jf)

    def transform(t:T):Seq[WriteMessage[T,NotUsed]] = {
      // Key must be uqique to time series (it will be ID+Timestamp)
      // For non-timestamp based it will be
      val id = t.getKey
      if(id.isDefined)
        // Upsert with a new ID. 
        // It will update if ID already exists
        Seq(WriteMessage.createUpsertMessage(id.get.toString, t))
      else {
        // Insert always new record with automatically generated key
        // DUPLICATES !
        Seq(WriteMessage.createIndexMessage(t))
      }
    }
  }

  // JsonWriter Tester
  class ToJson[T <: Ingestable](uri:String)(implicit fmt:JsonFormat[T]) {
    import spray.json._

    def sink():Sink[T,Any] = Sink.foreach(t => { println(s"${t.toJson.prettyPrint}"); System.out.flush })
      
    def transform(t:T):Seq[T] = {
      Seq(t)
    }
  }

  // Csv Tester
  class ToCsv[T <: Ingestable](uri:String) {
    //def sink():Sink[T,Any] = Sink.foreach(t => {println(t.toCSV); System.out.flush()})

    def sink(flush:Boolean = true):Sink[T,Any] =
      Flow[T]
        .map(o => if(o!=null) ByteString(o.toCSV+"\n") else ByteString())
        .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.both)

    def transform(t:T):Seq[T] = {
      Seq(t)
    }
  }

  // ------------------------------------------------------------------------------------------------------------------
  case class AttributeActor(a: ActorRef) extends Attributes.Attribute

  class FromWebsocket[T <: Ingestable](uri:String,buffer:Int = 1024,helloMsg:Option[String] = None,headers:Seq[HttpHeader] = Seq())
    (implicit as:ActorSystem,timeout:FiniteDuration) {
    
    val log = Logger(this.toString)

    val webSocketFlow = Http()
      .webSocketClientFlow(
        WebSocketRequest(
          uri,
          extraHeaders = headers
      ))
    
    val (a,s0) = Source
      .actorRef[TextMessage](buffer,OverflowStrategy.fail)
      .preMaterialize()

    log.info(s"[${uri}]: Actor=${a}")
    val s1 = s0
      .viaMat(webSocketFlow)(Keep.both) // keep the materialized Future[WebSocketUpgradeResponse]      
      .addAttributes(Attributes(AttributeActor(a)))
        
    def source() = s1.mapAsync(parallelism = 2)( m => {
      log.debug(s"<- ${uri} ['${m}']")
      m match {
        case txt: TextMessage.Strict => 
          Future.successful(ByteString(txt.text))
        case bin: BinaryMessage.Strict => 
          Future.successful(ByteString(bin.asTextMessage.getStrictText))
        case TextMessage.Streamed(txtStream) => 
          log.debug(s"${txtStream}")
          val f = txtStream
            .completionTimeout(timeout)
            // .runFold(new StringBuilder())((b, s) => b.append(s))
            // .map(b => ByteString(b.toString))
            .runFold(ByteString())((b, s) => {
              log.debug(s"${txtStream}: chunk:='${s}'")
              b.++(ByteString(s))
            })        
          f
                  
        case msg => 
          Future.successful(m.asBinaryMessage.getStrictData)
      }
    })

    if(helloMsg.isDefined) {
      log.info(s"HELLO: '${helloMsg.get}' -> ${uri}")
      a ! TextMessage.Strict(helloMsg.get)
    }

    def actor() = a
  }

}


