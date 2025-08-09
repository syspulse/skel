package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext}

import scala.annotation.tailrec

import spray.json._

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.ingest._

import io.syspulse.skel.serde.ParqIgnore

import akka.actor.ActorSystem

case class Recur(v:Int,recur:Array[Recur])

// recursive type is to test Parquet4s
case class Textline(txt:String,recur:Option[Array[Recur]] = None) extends skel.Ingestable {
  override def getKey: Option[Any] = Some(txt.hashCode())
  override def toString = txt
}

// customr Protocol to print embedded json in txt
trait TextlineJsonProtocol extends DefaultJsonProtocol {
  private val log = Logger(s"${this}")

  implicit object TextlineJsonFormat extends RootJsonFormat[Textline] {
    def write(t: Textline) = {
      if(t.txt.isBlank()) {
        JsObject()
      }
      else {      
        try {
          t.txt.parseJson
        } catch {
          case e:Exception =>
            log.warn(s"failed to convert to json: ${e.getMessage()}")
            //JsNull
            JsString(t.txt)            
        }
      }
    }

    def read(value: JsValue) = value match {
      case JsString(str) => Textline(str)
      case _ => deserializationError("plain text expected")
    }
  }

  implicit val jq_recur:JsonFormat[Recur] = lazyFormat(jsonFormat2(Recur))
  implicit val fmt = jsonFormat2(Textline.apply _)
}

object TextlineJson extends TextlineJsonProtocol 
import TextlineJson._

object ParqRecur extends ParqIgnore[Recur] 
import ParqRecur._

// object ParqTextline extends ParqIgnore[Textline] 
// import ParqTextline._

class PipelineTextline(feed:String,output:String)(implicit config:Config,as:Option[ActorSystem] = None) extends 
      Pipeline[String,String,Textline](
        feed,output,
        config.throttle,
        config.delimiter,
        config.buffer,
        throttleSource = config.throttleSource,
        format = config.format) {
  
  //private val log = Logger(s"${this}")
  implicit val ex:ExecutionContext = ExecutionContext.global
      
  override def getFileLimit():Long = config.limit
  override def getFileSize():Long = config.size

  // deduplication
  def processDedup:Flow[String,String,_] = Flow[String]
    .map(s => s)
    .groupedWithin(Int.MaxValue,FiniteDuration(2000L,TimeUnit.MILLISECONDS))
    .statefulMapConcat { () =>
      // Create a function to maintain a set of seen message IDs for each key
      var state = List.empty[String]
      var lastTs = System.currentTimeMillis()
      (mm) => {
        //val currentWindowStart = eventTime - windowDuration.toMillis
        val uniq = mm.filter(m => ! state.find(_ == m).isDefined)
        state =  state.prependedAll( uniq )
        val now = System.currentTimeMillis()
        if( (now - lastTs) > 2000L * 3 ) {
          state = state.take(2)
          lastTs = now
        }

        Console.err.println(s"Group: ${uniq} (state=${state})")
        uniq
      }
    }

  def processNone:Flow[String,String,_] = Flow[String]
    .map(s => s)

  def processPrint:Flow[String,String,_] = Flow[String]
    .map(s => {
      println(s"print: ${s}")
      s
    })
    
  override def process:Flow[String,String,_] = {
    val ff = config.params.map(_.toLowerCase match {
      case "dedup" => processDedup
      case "none" | "map" => processNone
      case "print" => processPrint
      case _ => processNone
    })
    
    def pipe(ff:List[Flow[String,String,_]]):Flow[String,String,_] = {
      ff match {
        case Nil => processNone
        case f :: Nil => f
        case f :: ff => f.via(pipe(ff))        
      }
    }

    pipe(ff.toList).log("textline")
  }
    
  override def parse(data: String): Seq[String] = {
    if(config.delimiter.isEmpty())
      Seq(data)
    else
      data.split(config.delimiter).toSeq
  }

  override def transform(txt: String): Seq[Textline] = {
    //Seq(Textline(s"[${countBytes},${countInput},${countObj},${countOutput}]: ${t}"))
    val t = Textline(txt)
    Seq(t)
  }

  override def source(feed:String):Source[ByteString,_] = {    
    feed.split("://").toList match {
      case "twitter" :: _ => 
        skel.twitter.Twitter.fromTwitter(feed)
      case "coingecko" :: _ => 
        skel.coingecko.Coingecko.fromCoingecko(feed)
      case _ => 
        super.source(feed)
    }
    
  }
}
