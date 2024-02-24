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

import io.syspulse.skel.Ingestable
import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticClient
import io.syspulse.skel.uri.ElasticURI
import io.syspulse.skel.uri.KafkaURI

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

object Flows {
  val log = Logger(this.toString)

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
      log.info(s"Restating -> Sink(${s})...")
      s
    }

  def formatter[O <: Ingestable](o:O,format:String)(implicit fmt:JsonFormat[O]):ByteString = {
    import spray.json._
    format match {
      case "raw" => ByteString(o.toRaw)
      case "jsonp" => ByteString(o.toJson.prettyPrint)
      case "json" => ByteString(o.toJson.compactPrint)
      case "csv" => ByteString(o.toCSV)
      case _ => ByteString(o.toLog)
    }
  }
  
  def fromCron(expr:String)(implicit as:ActorSystem):Source[ByteString, NotUsed] = {
    import com.typesafe.akka.extension.quartz.QuartzSchedulerExtension 
    //import akka.stream.typed.scaladsl.ActorSource

    val cronSource = Source.actorRef[ByteString](
      bufferSize = 100,
      overflowStrategy = OverflowStrategy.dropHead //OverflowStrategy.fail // <- convenient for testing
    ).map(t => {
        log.info(s"tick: ${t.utf8String}")
        ByteString(s"${System.currentTimeMillis()}\n")
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

  def fromClock(expr:String) = {
    val freq = FiniteDuration(Duration.create(expr).toMillis,TimeUnit.MILLISECONDS)
    Source
      .tick(FiniteDuration(0L,TimeUnit.MILLISECONDS),freq,"")
      .map(_ => ByteString(s"${System.currentTimeMillis()}"))      
  }

  def toNull = Sink.ignore
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
      
  def fromHttp(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192, retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    //val s = Source.future(fromHttpFuture(req))
    val s = RestartSource.onFailuresWithBackoff(retry) { () =>
      log.info(s"${retry}: ==> ${req}")
      Source.futureSource {
        Flows.fromHttpFuture(req)
      }
    }
      
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))    
  }

  def fromHttpRestartable(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    fromHttp(req,frameDelimiter,frameSize,retry)
  }

  def fromHttpList(reqs: Seq[HttpRequest],par:Int = 1, frameDelimiter:String="\n",frameSize:Int = 8192,throttle:Long = 10L,retry:RestartSettings=retrySettingsDefault)(implicit as:ActorSystem,timeout:FiniteDuration) = {
    // Http().singleRequest does not respect mapAsync for parallelization !!!
    val s = 
      Source(reqs)
      .throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .flatMapConcat(req => Flows.fromHttpRestartable(req,frameDelimiter,frameSize,retry))
    
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
        Flows.fromHttpRestartable(req, frameDelimiter, frameSize)
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

// ==================================================================================================
// Sinks
// ==================================================================================================  

  def toFile[T <: Ingestable](file:String)(implicit fmt:JsonFormat[T]) = {
    import akka.event.Logging
    import spray.json._

    if(file.trim.isEmpty) 
      Sink.ignore 
    else {
      //toSinkRestart({
        Flow[T]
          .map(t => if(file.endsWith(".json")) 
              s"${t.toJson}\n" 
            else if(file.endsWith(".csv")) 
              s"${t.toCSV}\n" 
            else 
              s"${t.toLog}\n"
          )
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
    
    val sink = 
    if(uri.trim.isEmpty) {
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
          log.debug(s"body(${body}) --> ${uri}")
          
          val http =
            Http()
            .singleRequest(
              HttpRequest(
                HttpMethods.POST,
                uri = uri, 
                entity = HttpEntity(ContentTypes.`application/json`,body),
                headers = Seq(Authorization(OAuth2BearerToken(sys.env.get("API_KEY").getOrElse(""))))
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
              log.error(s"failed connection: ${uri}",f)
              throw new Exception(s"${f}")
          })
          
          f
        })
        .log(s"${this}")
        .map(r => {
          log.debug(s"res: '${r}'")
          r
        })
        .toMat(
          Sink.ignore
        )(Keep.right)
    }

    RestartSink.withBackoff[T](retrySettingsDefault) { () =>
      log.info(s"Connection --> ${uri}")
      sink
    }
  }

  // ===== WebSocket Sink
  def toWsServer[T <: Ingestable](uri:String,format:String="",timeout:Long = 1000L*60*60*24)(implicit as:ActorSystem,fmt:JsonFormat[T]) = { 
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
      
      def broadcast(msg:String):Try[Unit] = {
        Success(this.broadcastText(msg) )        
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
    
      val (actor,source0) = Source
        .actorRef[String](1,OverflowStrategy.dropTail)
        .preMaterialize()   
      
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


  def toFileNew[T <: Ingestable](file:String,rotator:(T,String) => String)(implicit mat: Materializer,fmt:JsonFormat[T]) = {
    import spray.json._  
    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[T].map( t => {
        val out = if(file.endsWith(".json")) 
              s"${t.toJson}\n" 
            else if(file.endsWith(".csv")) 
              s"${t.toCSV}\n" 
            else 
              s"${t.toLog}\n"
        Source
          .single(ByteString(out))
          .toMat(FileIO.toPath(
            Paths.get(rotator(t,file)),options =  Set(WRITE, CREATE))
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

  def toHiveFileSize[T <: Ingestable](file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit fmt:JsonFormat[T]) = {
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
        .map(t => if(file.endsWith(".json")) 
              s"${t.toJson}\n" 
            else if(file.endsWith(".csv")) 
              s"${t.toCSV}\n" 
            else 
              s"${t.toLog}\n"
        )
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger))(Keep.right)
  }

  // S3 mounted as FileSystem/Volume
  // Does not support APPEND 
  def toFS3[T <: Ingestable](file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit rotator:Rotator,fmt:JsonFormat[T]) = {
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
        .map(t => if(file.endsWith(".json")) 
              s"${t.toJson}\n" 
            else if(file.endsWith(".csv")) 
              s"${t.toCSV}\n" 
            else 
              s"${t.toLog}\n"
        )
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
      log.info(s"Restating -> Kafka(${uri})...")
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

  def toStdout[O](flush:Boolean = false): Sink[O, Future[IOResult]] = toPipe(flush,System.out)
  def toStderr[O](flush:Boolean = false): Sink[O, Future[IOResult]] = toPipe(flush,System.err)
  def toPipe[O](flush:Boolean,pipe:java.io.PrintStream): Sink[O, Future[IOResult]] = {
    Flow[O]
      .map(o => if(o!=null) ByteString(o.toString+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => pipe,flush))(Keep.right)  
  }
  
  // This sink returns Future[Done] and not possible to wait for completion of the flow
  //def toStdout() = Sink.foreach(println _)

  // JDBC
  def toJDBC[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {     
    val jdbc = new ToJDBC[T](uri,None).flow()
    
    Flow[T]
      .via(jdbc)
      .log("jdbc")
      .toMat(Sink.ignore)(Keep.right)
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
  
  val sink0 = sink(kafkaUri.broker,Set(kafkaUri.topic))

  def sink():Sink[T,_] = sink0
  
  override def transform(t:T):ByteString = {
    // val o = if(kafkaUri.isRaw) 
    //   ByteString(t.toString)
    // else
    //   ByteString(t.toJson.compactPrint)
    Flows.formatter(t,formatOutput)
  }  
}

class FromKafka[T <: Ingestable](uri:String) extends skel.ingest.kafka.KafkaSource[T] {
  val kafkaUri = KafkaURI(uri)
    
  def source():Source[ByteString,_] = source(kafkaUri.broker,Set(kafkaUri.topic),kafkaUri.group,offset = kafkaUri.offset)
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
