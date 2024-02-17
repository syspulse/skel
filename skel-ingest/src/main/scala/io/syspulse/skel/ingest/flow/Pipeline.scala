package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import akka.util.ByteString
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

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

// throttleSource - reduce load on Source (e.g. HttpSource)
// throttle - delay objects downstream
abstract class Pipeline[I,T,O <: skel.Ingestable](feed:String,output:String,
  throttle:Long = 0, delimiter:String = "\n", buffer:Int = 8192, chunk:Int = 1024 * 1024,throttleSource:Long=100L,format:String="")
  (implicit fmt:JsonFormat[O], parqEncoders:ParquetRecordEncoder[O],parsResolver:ParquetSchemaResolver[O])  extends IngestFlow[I,T,O]() {
  
  private val log = Logger(s"${this}")
  
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
      case "null" :: _ => Flows.fromNull
      case "kafka" :: _ => Flows.fromKafka(feed)
      case "http" :: _ => {
        if(feed.contains(",")) {
          Flows.fromHttpList(feed.split(",").toIndexedSeq.map(uri => 
            HttpRequest(uri = uri.trim).withHeaders(Accept(MediaTypes.`application/json`))),
            frameDelimiter = delimiter,frameSize = buffer, throttle =  throttleSource)
        }
        else
          // ATTENTION!
          Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
          //Flows.fromHttpRestartable(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
      }
      case "https" :: _ => Flows.fromHttp(HttpRequest(uri = feed).withHeaders(Accept(MediaTypes.`application/json`)),frameDelimiter = delimiter,frameSize = buffer)
      
      case "listen" :: uri :: Nil => Flows.fromHttpServer(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case ("server" | "http:server" | "https:server") :: uri :: Nil => Flows.fromHttpServer(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)

      case "tcp" :: uri :: Nil => Flows.fromTcpClientUri(uri,chunk,frameDelimiter = delimiter, frameSize = buffer)

      case "file" :: fileName :: Nil => Flows.fromFile(fileName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      
      case "tail" :: fileName :: Nil => Flows.fromTail(fileName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "tails" :: Nil => Flows.fromDirTail("./",chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "tails" :: dirName :: Nil => Flows.fromDirTail(dirName,chunk,frameDelimiter = delimiter, frameSize = buffer)
      
      case "dir" :: Nil => Flows.fromDir("./",0,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dir" :: dirName :: Nil => Flows.fromDir(dirName,0,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dirs" :: Nil => Flows.fromDir("./",Int.MaxValue,chunk,frameDelimiter = delimiter, frameSize = buffer)
      case "dirs" :: dirName :: Nil => Flows.fromDir(dirName,Int.MaxValue,chunk,frameDelimiter = delimiter, frameSize = buffer)

      case "stdin" :: _ => Flows.fromStdin(frameDelimiter = delimiter, frameSize = buffer)
            
      case "cron" :: expr :: next :: rest => 
        val cronSource = Flows.fromCron(expr)
        val nextSource:Source[ByteString,_] = source(next + "://" + rest.mkString(""))
        cronSource.flatMapConcat( tick => nextSource)
        
      // test cron
      case "cron" :: Nil => Flows.fromCron("*/1 * * * * ?")

      // test clock 
      case "clock" :: freq :: Nil => Flows.fromClock(freq)
      case "clock" :: Nil => Flows.fromClock("1 sec")

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

      case "" :: Nil => Flows.fromStdin(frameDelimiter = delimiter, frameSize = buffer) 
      case file :: Nil => Flows.fromFile(file,chunk,frameDelimiter = delimiter,frameSize = buffer)
      case _ =>         
        Flows.fromStdin(frameDelimiter = delimiter, frameSize = buffer) 
    }
    src0
  }

  override def sink() = {
    sink(output)
  }

  def getRotator():Flows.Rotator = new Flows.RotatorCurrentTime()

  def getFileLimit():Long = Long.MaxValue
  def getFileSize():Long = Long.MaxValue
  
  def sink(output:String) = {
    log.info(s"output=${output}")
        
    val sink = output.split("://").toList match {
      case "null" :: _ => Flows.toNull
      
      case "json" :: _ => Flows.toJson[O](output,pretty=false)(fmt)
      case "pjson" :: _ => Flows.toJson[O](output,pretty=true)(fmt)
      case "csv" :: _ => Flows.toCsv(output)
      case "log" :: _ => Flows.toLog(output)

      case "kafka" :: _ => Flows.toKafka[O](output)(fmt)
      case "elastic" :: _ => Flows.toElastic[O](output)(fmt)
      
      case "file" :: fileName :: Nil => Flows.toFile(fileName)
      case "files" :: fileName :: Nil => Flows.toHiveFileSize(fileName)
      case "hive" :: fileName :: Nil => Flows.toHive(fileName)(getRotator())

      case "fs3" :: fileName :: Nil => Flows.toFS3(fileName,getFileLimit(),getFileSize())(getRotator(),fmt)
      case "parq" :: fileName :: Nil => Flows.toParq[O](fileName,getFileLimit(),getFileSize())(getRotator(),parqEncoders,parsResolver)

      // test to create new file for every object
      // TODO: remove it
      case "filenew" :: fileName :: Nil => Flows.toFileNew(fileName,(o:O,file) => file + o.getId.getOrElse("").toString)
      
      // funny test implementation for custom timestamp into the past 1000 years
      // TODO: remove it !
      case "past" :: fileName :: Nil => 
        Flows.toHive(fileName)(new Flows.RotatorTimestamp( () => ZonedDateTime.ofInstant(Instant.now, ZoneId.systemDefault).minusYears(1000).toInstant().toEpochMilli() ))

      case "http" :: uri :: Nil => Flows.toHTTP[O](output,pretty=false)
      case "https" :: uri :: Nil => Flows.toHTTP[O](output,pretty=false)

      case "stdout" :: _ => Flows.toStdout()
      case "stderr" :: _ => Flows.toStderr()

      case "jdbc" :: _ => Flows.toJDBC[O](output)(fmt)
      case "postgres" :: _ => Flows.toJDBC[O](output)(fmt)
      case "mysql" :: _ => Flows.toJDBC[O](output)(fmt)

      case "server:ws" :: uri :: Nil => Flows.toWsServer[O](uri,format)

      case "" :: Nil => Flows.toStdout()
      case _ => 
        Flows.toFile(output)
    }
    sink
  }

}