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
import io.syspulse.skel.ingest.uri.KafkaURI

import spray.json.JsonFormat

object Flows {
  def toNull = Sink.ignore

  def fromHttpFuture(req: HttpRequest)(implicit as:ActorSystem) = Http()
    .singleRequest(req)
    .flatMap(res => res.entity.dataBytes.runReduce(_ ++ _))

  def fromHttp(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192)(implicit as:ActorSystem) = {
    val s = Source.future(fromHttpFuture(req))
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
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

  def toStdout[O](flush:Boolean = false): Sink[O, Future[IOResult]] = 
    Flow[O]
      .map(o => if(o!=null) ByteString(o.toString+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  // This sink returns Future[Done] and not possible to wait for completion of the flow
  //def toStdout() = Sink.foreach(println _)

  def toFile(file:String) = {
    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[Ingestable]
        .map(t=>s"${t.toLog}\n")
        .map(ByteString(_))
        .toMat(FileIO.toPath(
          Paths.get(Util.pathToFullPath(Util.toFileWithTime(file))),options =  Set(WRITE, CREATE))
        )(Keep.right)
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
        .map(t=>s"${t.toLog}\n")
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger))(Keep.right)
  }

  def toElastic[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {
    val es = new ToElastic[T](uri)(fmt)
    Flow[T]
      .mapConcat(t => es.transform(t))
      .toMat(es.sink())((Keep.right))
  }

  def toKafka[T <: Ingestable](uri:String) = {
    val kafka = new ToKafka[T](uri)
    Flow[T]
      .to(kafka.sink())
  }

  def fromKafka[T <: Ingestable](uri:String) = {
    val kafka = new FromKafka[T](uri)
    kafka.source()
  }

  def toJson[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {
    val es = new ToJson[T](uri)(fmt)
    Flow[T]
      .mapConcat(t => es.transform(t))
      .to(es.sink())
  }
}


// Kafka Client Flows
class ToKafka[T <: Ingestable](uri:String) extends skel.ingest.kafka.KafkaSink[T] {
  val kafkaUri = KafkaURI(uri)
  
  val sink0 = sink(kafkaUri.broker,Set(kafkaUri.topic))

  def sink():Sink[T,_] = sink0
  
  override def transform(t:T):ByteString = {
    ByteString(t.toString)
  }  
}

class FromKafka[T <: Ingestable](uri:String) extends skel.ingest.kafka.KafkaSource[T] {
  val kafkaUri = KafkaURI(uri)
    
  def source():Source[ByteString,_] = source(kafkaUri.broker,Set(kafkaUri.topic),kafkaUri.group)
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
    val id = t.getId
    if(id.isDefined)
      // Upsert !!!
      Seq(WriteMessage.createUpsertMessage(id.get.toString, t))
    else
      // Upsert is not supported without id
      Seq(WriteMessage.createIndexMessage(t))
  }
}

// JsonWriter Tester
class ToJson[T <: Ingestable](uri:String)(implicit fmt:JsonFormat[T]) {
  import spray.json._

  def sink():Sink[T,Any] = Sink.foreach(t => println(s"${t.toJson.prettyPrint}"))
    
  def transform(t:T):Seq[T] = {
    Seq(t)
  }
}

