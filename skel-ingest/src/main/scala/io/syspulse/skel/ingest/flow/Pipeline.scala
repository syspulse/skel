package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import com.typesafe.config.ConfigFactory

import akka.util.ByteString
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.{Sink,Source,Flow}

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest._
import io.syspulse.skel.ingest.store._

import spray.json._
import java.util.concurrent.TimeUnit
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import com.github.mjakubowski84.parquet4s.ParquetRecordEncoder
import com.github.mjakubowski84.parquet4s.ParquetSchemaResolver

import akka.actor.ActorSystem

// throttleSource - reduce load on Source (e.g. HttpSource)
// throttle - delay objects downstream
// cap - capacity (internal buffer, like Actor)
abstract class Pipeline[I,T,O <: skel.Ingestable](feed:String,output:String,
  throttle:Long = 0, delimiter:String = "\n", buffer:Int = 8192, chunk:Int = 1024 * 1024,throttleSource:Long=100L,format:String="",cap:Int=10000)
  (implicit fmt:JsonFormat[O], parqEncoders:ParquetRecordEncoder[O],parsResolver:ParquetSchemaResolver[O],as:Option[ActorSystem] = None) 
  extends Flows 
  with IngestFlow[I,T,O]()  {
  
  private val log = Logger(s"${this}")
  override implicit val system:ActorSystem = {    
    as.getOrElse({
      val name = "ActorSystem-IngestFlow"
      val config = ConfigFactory.load()
      val as = try {
        ActorSystem(name, config.getConfig(name).withFallback(config))
      } catch {
        case e:Exception => 
          log.debug(s"failed to find ActorSystem config: '${name}': using default config. (err=${e.getMessage})")
          ActorSystem(name)
      }
      as
    })
  }
  log.info(s"system=${system}")

  implicit def timeout:FiniteDuration = FiniteDuration(5000, TimeUnit.MILLISECONDS)
  
  def shaping:Flow[T,T,_] = {
    if(throttle != 0L)
      Flow[T].throttle(1,FiniteDuration(throttle,TimeUnit.MILLISECONDS))          
    else
      Flow[T].map(t => t)
  }
  
  override def source() = {
    source(feed)
  }

  def source(feed:String):Source[ByteString,_] = {
    log.info(s"feed=${feed}")
    val src0 = feed.split("://").toList match {
      case "null" :: _ => fromNull
      case "kafka" :: _ => fromKafka(feed)
      case "http" :: _ => {
        if(feed.contains(",")) {
          fromHttpList(feed.split(",").toIndexedSeq.map(uri => 
            HttpRequest(uri = uri.trim).withHeaders(Accept(MediaTypes.`application/json`))),
            frameDelimiter = delimiter,frameSize = buffer, throttle =  throttleSource)
        }
        else
          // ATTENTION!
          fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
          //Flows.fromHttpRestartable(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
      }
      case "https" :: _ => fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
      
      case "listen" :: uri :: Nil => fromHttpServer(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case ("server" | "http:server" | "https:server") :: uri :: Nil => fromHttpServer(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)

      case "tcp" :: uri :: Nil => fromTcpClientUri(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)
      
      case ("ws" | "wss") :: _ => fromWebsocket(feed,frameDelimiter = delimiter, frameSize = buffer, helloMsg = sys.env.get("WS_HELLO_MSG"))

      case "file" :: fileName :: Nil => fromFile(fileName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      
      case "tail" :: fileName :: Nil => fromTail(fileName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "tails" :: Nil => fromDirTail("./",chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "tails" :: dirName :: Nil => fromDirTail(dirName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      
      case "dir" :: Nil => fromDir("./",0,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dir" :: dirName :: Nil => fromDir(dirName,0,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dirs" :: Nil => fromDir("./",Int.MaxValue,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dirs" :: dirName :: Nil => fromDir(dirName,Int.MaxValue,chunk,frameDelimiter = delimiter, frameSize = buffer)

      case "stdin" :: _ => fromStdin(frameDelimiter = delimiter, frameSize = buffer)
            
      case "cron" :: expr :: next :: rest => 
        val cronSource = fromCron(expr)
        val nextSource:Source[ByteString,_] = source(next + "://" + rest.mkString(""))
        cronSource.flatMapConcat( tick => nextSource)

      case "cron" :: expr :: Nil => fromCron(expr)
        
      // test cron
      case "cron" :: Nil => fromCron("*/1 * * * * ?")

      // test clock 
      case "clock" :: freq :: next :: rest => 
        val clockSource = fromClock(freq)
        val nextSource:Source[ByteString,_] = source(next + "://" + rest.mkString(""))
        clockSource.flatMapConcat( tick => nextSource)
      case "clock" :: freq :: Nil => fromClock(freq)
      case "clock" :: Nil => fromClock("1 sec")

      case "tick" :: expr :: next :: rest =>
        val (tickInitial,tickInterval) = expr.split(",").toList match {
          case tickInitial :: tickInterval :: Nil => (tickInitial.toLong,tickInterval.toLong)
          case tickInterval :: Nil => (0L,tickInterval.toLong)
          case _ => (0L,1000L)
        }
        val cronSource = Source.tick[ByteString](
          FiniteDuration(tickInitial.toLong,TimeUnit.MILLISECONDS),FiniteDuration(tickInterval,TimeUnit.MILLISECONDS),
          ByteString(s"${System.currentTimeMillis()}")
        )
        val nextSource:Source[ByteString,_] = source(next + "://" + rest.mkString(""))
        cronSource.flatMapConcat( tick => nextSource)

      case "twitter" :: uri :: Nil => fromTwitter(uri,frameDelimiter = delimiter,frameSize = buffer)

      case "akka" :: uri :: Nil => fromAkka(feed)

      case "" :: Nil => fromStdin(frameDelimiter = delimiter, frameSize = buffer) 
      case file :: Nil => fromFile(file,chunk,frameDelimiter = delimiter,frameSize = buffer)
      case _ =>         
        fromStdin(frameDelimiter = delimiter, frameSize = buffer) 
    }
    src0
  }

  override def sink():Sink[O,_] = {
    sink(output)
  }

  def sink(output:String):Sink[O,_] = {
    sinking[O](output)
  }

  def getRotator():Rotator = new RotatorCurrentTime()

  def getFileLimit():Long = Long.MaxValue
  def getFileSize():Long = Long.MaxValue
  
  def sinking[O <: skel.Ingestable](output:String)(implicit fmt:JsonFormat[O], parqEncoders:ParquetRecordEncoder[O],parsResolver:ParquetSchemaResolver[O]):Sink[O,_] = {
    log.info(s"output=${output}")
        
    val sink = output.split("://").toList match {
      case "null" :: Nil => toNull[O]()(fmt)
      case "null" :: delay :: Nil => toNull[O](delay.toLong)(fmt)
      
      case "json" :: _ => toJson[O](output,pretty=false)(fmt)
      case "pjson" :: _ => toJson[O](output,pretty=true)(fmt)
      case "csv" :: _ => toCsv(output)
      case "log" :: _ => toLog(output)
      
      case "kafka" :: _ => toKafka[O](output,format)(fmt)
      case "elastic" :: _ => toElastic[O](output)(fmt)
      
      case "file" :: fileName :: Nil => toFile(fileName)
      case "files" :: fileName :: Nil => toHiveFileSize(fileName)
      case "hive" :: fileName :: Nil => toHive(fileName)(getRotator())

      case "fs3" :: fileName :: Nil => toFS3(fileName,getFileLimit(),getFileSize())(getRotator(),fmt)
      case "parq" :: fileName :: Nil => toParq[O](fileName,getFileLimit(),getFileSize())(getRotator(),parqEncoders,parsResolver)

      // test to create new file for every object
      // TODO: remove it
      case "filenew" :: fileName :: Nil => toFileNew(fileName,(o:O,file) => file + o.getId.getOrElse("").toString)
      
      // funny test implementation for custom timestamp into the past 1000 years
      // TODO: remove it !
      case "past" :: fileName :: Nil => 
        toHive(fileName)(new RotatorTimestamp( () => ZonedDateTime.ofInstant(Instant.now, ZoneId.systemDefault).minusYears(1000).toInstant().toEpochMilli() ))

      case "http" :: uri :: Nil => toHTTP[O](output,format)
      case "https" :: uri :: Nil => toHTTP[O](output,format)
      
      case "jdbc" :: _ => toJDBC[O](output)(fmt)
      case "postgres" :: _ => toJDBC[O](output)(fmt)
      case "mysql" :: _ => toJDBC[O](output)(fmt)

      case "server:ws" :: uri :: Nil => toWebsocketServer[O](uri,format,buffer = cap)
      case "ws:server" :: uri :: Nil => toWebsocketServer[O](uri,format,buffer = cap)

      // stderr which do not use StreamConverters (slower but never blocking)
      case "out" :: _ => toOut()
      case "err" :: _ => toErr()

      case "stdout" :: _ => toStdout(format=format)
      case "stderr" :: _ => toStderr(format=format)

      case "akka" :: uri :: Nil => toAkka[O](output,format)      

      case "" :: Nil => toStdout(format=format)
      case _ => toFile(output)
    }
    sink
  }

}