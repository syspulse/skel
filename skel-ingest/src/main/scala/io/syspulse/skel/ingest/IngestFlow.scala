package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import java.nio.file.StandardOpenOption._

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
import akka.stream.alpakka.file.scaladsl.LogRotatorSink

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import io.syspulse.skel.Ingestable
import io.syspulse.skel
import io.syspulse.skel.util.Util
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.Http
import java.util.concurrent.TimeUnit

trait IngestFlow[T,D] {
  private val log = Logger(s"${this}")
  implicit val system = ActorSystem("ActorSystem-IngestFlow")
  
  val retrySettings = RestartSettings(
    minBackoff = FiniteDuration(3000,TimeUnit.MILLISECONDS),
    maxBackoff = FiniteDuration(10000,TimeUnit.MILLISECONDS),
    randomFactor = 0.2
  )
  //.withMaxRestarts(10, 5.minutes)

  var count:Long = 0L

  var defaultSource:Option[Source[ByteString,_]] = None
  var defaultSink:Option[Sink[D,_]] = None

  def parse(data:String):Seq[T]

  def sink():Sink[D,Any] = if(defaultSink.isDefined) defaultSink.get else IngestFlow.toStdout()

  def sink0():Sink[D,Any] = Sink.ignore

  def source():Source[ByteString,_] = if(defaultSource.isDefined) defaultSource.get else IngestFlow.fromStdin()

  def flow:Flow[T,T,_] = Flow[T].map(t => t)

  def transform(t:T):Seq[D]

  def debug = Flow.fromFunction( (data:ByteString) => { log.debug(s"data=${data}"); data})

  def counter = Flow[ByteString].map(t => { count = count + 1; t})

  def run() = {    
    val flowing =
      source()
      .via(debug)
      .via(counter)      
      .mapConcat(txt => parse(txt.utf8String))
      .via(flow)
      .viaMat(KillSwitches.single)(Keep.right)
      .mapConcat(t => transform(t))
      .log("ingest-flow")
      .alsoTo(sink0())
      .runWith(sink())

    //val r = Await.result(result, timeout())
    log.info(s"flow: ${flowing}")
    flowing
  }

  def from(src:Source[ByteString,_]):IngestFlow[T,D] = {
    defaultSource = Some(src)
    this
  }
}

object IngestFlow {
  def fromHttpFuture(req: HttpRequest)(implicit as:ActorSystem) = Http()
    .singleRequest(req)
    .flatMap(res => res.entity.dataBytes.runReduce(_ ++ _))

  def fromHttp(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192)(implicit as:ActorSystem) = Source.future(fromHttpFuture(req))
    .via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))

  def fromStdin():Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
  // this is non-streaming simple ingester. Reads full file, flattens it and parses into Stream of Tms objects
  def fromFile(file:String = "/dev/stdin",chunk:Int = 0,frameDelimiter:String="\r\n",frameSize:Int = 8192):Source[ByteString, Future[IOResult]] =  {
    val filePath = Util.pathToFullPath(file)
    FileIO
      .fromPath(Paths.get(filePath),chunkSize = if(chunk==0) Files.size(Paths.get(filePath)).toInt else chunk)
      .via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def toStdout() = Sink.foreach(println _)

  def toFile(file:String) = {
    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[Ingestable]
        .map(t=>s"${t.toSimpleLog}\n")
        .map(ByteString(_))
        .to(FileIO.toPath(
          Paths.get(Util.pathToFullPath(Util.toFileWithTime(file))),options =  Set(WRITE, CREATE))
        )
  }

  def toHiveFile(file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue) = {

    val fileRotateTrigger: () => ByteString => Option[Path] = () => {
      var currentFilename: Option[String] = None
      var init = false
      val max = 10 * 1024 * 1024
      var count: Long = 0L
      var size: Long = 0L
      var currentTs = System.currentTimeMillis
      (element: ByteString) => {
        if(init && (count < fileLimit && size < fileSize)) {
          count = count + 1
          size = size + element.size
          None
        } else {
          currentFilename = Some(Util.pathToFullPath(Util.toFileWithTime(file)))
          
          count = 0L
          size = 0L
          init = true
          
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
      Flow[Ingestable]
        .map(t=>s"${t.toSimpleLog}\n")
        .map(ByteString(_))
        .to(LogRotatorSink(fileRotateTrigger))
  }

}