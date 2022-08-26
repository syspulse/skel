package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._

import java.nio.file.StandardOpenOption._

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
import akka.stream.alpakka.file.scaladsl.LogRotatorSink

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.ElasticsearchParams

import io.syspulse.skel.Ingestable
import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.elastic.ElasticClient
import io.syspulse.skel.ingest.uri.ElasticURI
import spray.json.JsonFormat

object Flows {
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

  def toElastic[T <: Ingestable](uri:String)(implicit fmt:JsonFormat[T]) = {
    val es = new ToElastic[T](uri)
    Flow[T]
      .mapConcat(t => es.transform(t))
      .to(es.sink())
  }
}


class ToElastic[T <: Ingestable](uri:String)(implicit val fmt:JsonFormat[T]) extends ElasticClient[T] {
  val elasticUri = ElasticURI(uri)
  connect(elasticUri.uri,elasticUri.index)


  def sink():Sink[WriteMessage[T,NotUsed],Any] = 
    ElasticsearchSink.create[T](
      ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
    )

  def transform(t:T):Seq[WriteMessage[T,NotUsed]] = {
    val index = t.getIndex
    if(index.isDefined)
      Seq(WriteMessage.createIndexMessage(index.get.toString, t))
    else
      Seq(WriteMessage.createIndexMessage("", t))
  }
}