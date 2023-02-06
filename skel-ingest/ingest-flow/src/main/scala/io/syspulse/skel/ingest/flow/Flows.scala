package io.syspulse.skel.ingest.flow

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

import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.ElasticsearchParams

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
import akka.http.scaladsl.model.StatusCodes.Success
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes

object Flows {
  val log = Logger(this.toString)

  def toNull = Sink.ignore
  def fromNull = Source.future(Future({Thread.sleep(Long.MaxValue);ByteString("")})) //Source.never

  def fromHttpFuture(req: HttpRequest)(implicit as:ActorSystem) = Http()
    .singleRequest(req)
    .flatMap(res => { 
      res.status match {
        case StatusCodes.OK => 
          val body = res.entity.dataBytes.runReduce(_ ++ _)
          log.debug(s"body=${body}")
          body
        case _ => 
          val body = Await.result(res.entity.dataBytes.runReduce(_ ++ _),FiniteDuration(1000L,TimeUnit.MILLISECONDS)).utf8String
          log.error(s"${req}: ${res.status}: body=${body}")
          throw new Exception(s"${req}: ${res.status}")
      }      
    })
      

  def fromHttp(req: HttpRequest,frameDelimiter:String="\n",frameSize:Int = 8192)(implicit as:ActorSystem) = {
    val s = Source.future(fromHttpFuture(req))
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def fromHttpList(req: Seq[HttpRequest],par:Int = 1, frameDelimiter:String="\n",frameSize:Int = 8192,throttle:Long = 10L)(implicit as:ActorSystem) = {
    // Http().singleRequest does not respect mapAsync for parallelization !!!
    val s = 
      Source(req)
      .throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .mapAsync(par)(r => Flows.fromHttpFuture(r)(as))
    
    if(frameDelimiter.isEmpty())
      s
    else
      s.via(Framing.delimiter(ByteString(frameDelimiter), maximumFrameLength = frameSize, allowTruncation = true))
  }

  def fromHttpListAsFlow(req: Seq[HttpRequest],par:Int = 1, frameDelimiter:String="\n",frameSize:Int = 8192,throttle:Long = 10L)(implicit as:ActorSystem) = {
    val f1 = Flow[String]
      .throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))
      .mapConcat(tick => {        
        req
      })
      .mapAsync(par)(r => {
        log.info(s"--> ${req}")
        Flows.fromHttpFuture(r)(as)
      })
      //.log(s"--> ${req}")
    
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
      isRotatable() && (nextTs != 0 && askTime() != nextTs)
    }

    override def rotate(file:String,count:Long,size:Long):Option[String]  = {
      val ts = askTime()
      nextTs = if(tsRotatable) ts else 0L
      Some(Util.pathToFullPath(Util.toFileWithTime(file,ts)))
    }
  }


  def toFileNew[O <: Ingestable](file:String,rotator:(O,String) => String)(implicit mat: Materializer) = {
      
    if(file.trim.isEmpty) 
      Sink.ignore 
    else
      Flow[O].map( t => {
        Source
          .single(ByteString(t.toLog))
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

  def toHiveFileSize(file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue) = {

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
      Flow[Ingestable]
        .map(t=>s"${t.toLog}\n")
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger))(Keep.right)
  }

  // S3 mounted as FileSystem/Volume
  // Does not support APPEND 
  def toFS3(file:String,fileLimit:Long = Long.MaxValue, fileSize:Long = Long.MaxValue)(implicit rotator:Rotator) = {
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
      Flow[Ingestable]
        .map(t=>s"${t.toLog}\n")
        .map(ByteString(_))
        .toMat(LogRotatorSink(fileRotateTrigger,fileOpenOptions = Set(StandardOpenOption.CREATE,StandardOpenOption.WRITE)))(Keep.right)
  }

  def toElastic[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {
    val es = new ToElastic[T](uri)(fmt)
    Flow[T]
      .mapConcat(t => es.transform(t))
      .toMat(es.sink())(Keep.right)
  }

  def toKafka[T <: Ingestable](uri:String) = {
    val kafka = new ToKafka[T](uri)
    Flow[T]
      .toMat(kafka.sink())(Keep.right)
  }

  def fromKafka[T <: Ingestable](uri:String) = {
    val kafka = new FromKafka[T](uri)
    kafka.source()
  }

  // def toJson[T <: Ingestable](uri:String)(fmt:JsonFormat[T]) = {
  //   val js = new ToJson[T](uri)(fmt)
  //   Flow[T]
  //     .mapConcat(t => js.transform(t))
  //     .to(js.sink())
  // }

  def toJson[T <: Ingestable](uri:String,flush:Boolean = false)(implicit fmt:JsonFormat[T]) = {
    import spray.json._
    Flow[T]
      .map(o => if(o!=null) ByteString(o.toJson.prettyPrint+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  }

  def toCsv[T <: Ingestable](uri:String,flush:Boolean = false):Sink[T, Future[IOResult]] = {
    Flow[T]
      .map(o => if(o!=null) ByteString(o.toCSV+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => System.out,flush))(Keep.right)
  }

  def toStdout[O](flush:Boolean = false): Sink[O, Future[IOResult]] = toPipe(flush,System.out)
  def toStderr[O](flush:Boolean = false): Sink[O, Future[IOResult]] = toPipe(flush,System.err)
  def toPipe[O](flush:Boolean,pipe:java.io.PrintStream): Sink[O, Future[IOResult]] = 
    Flow[O]
      .map(o => if(o!=null) ByteString(o.toString+"\n") else ByteString())
      .toMat(StreamConverters.fromOutputStream(() => pipe,flush))(Keep.right)
  // This sink returns Future[Done] and not possible to wait for completion of the flow
  //def toStdout() = Sink.foreach(println _)

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
