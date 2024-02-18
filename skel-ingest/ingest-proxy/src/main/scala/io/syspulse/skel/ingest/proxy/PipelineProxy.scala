package io.syspulse.skel.ingest.proxy

import scala.annotation.tailrec

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import spray.json._

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel.util.Util
import io.syspulse.skel.config._
import io.syspulse.skel.ingest._
import io.syspulse.skel.ingest.flow.Pipeline
import io.syspulse.skel.Ingestable

import com.github.tototoshi.csv._

// === Output String ===================================================
case class OutputString(str:String) extends Ingestable {
  override def getKey: Option[Any] = Some(str.hashCode())
  override def toString = str.toString
}
object OutputStringJson extends OutputStringJsonProtocol {

}
// custom Json to ignore json
trait OutputStringJsonProtocol extends DefaultJsonProtocol {
  private val log = Logger(s"${this}")

  implicit object OutputStringJsonFormat extends RootJsonFormat[OutputString] {
    def write(t: OutputString) = {
      JsString(t.str)      
    }

    def read(value: JsValue) = value match {
      case JsString(str) => OutputString(str)
      case _ => deserializationError("plain text expected")
    }
  }

  implicit val fmt = jsonFormat1(OutputString.apply _)
}

// === Transformer String ===================================================
case class TextString(o:Any) {
  def str:String = o.toString

  def obj:Any = o

  // ATTENTION: since we already split new lines, CSV should take only 1 line
  def fromCsv():TextString = {
    val csv = o match {
      case s:String => 
        val r = CSVReader.open(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(s.getBytes())))        
        r.readNext().getOrElse("")        
      case jv:ujson.Value.Value => 
        // ignore, incorrect state
        o
      case ss: List[String] => 
        // already 
        ss
      case _ => 
        val r = CSVReader.open(new java.io.InputStreamReader(new java.io.ByteArrayInputStream(o.toString.getBytes())))
        r.readNext().getOrElse("")
        //r.all()
    }
    
    TextString(csv)
  }

  def toCsv():TextString = {    
    val csv = o match {
      case s:String => 
        s

      case jv:ujson.Value.Value =>         
        val buf = new java.io.ByteArrayOutputStream()
        val r = CSVWriter.open(new java.io.OutputStreamWriter(buf))
        val csvValues = jv match {
          case ujson.Obj(obj) =>             
            obj.map{case(k,v) => v.toString.stripPrefix("\"").stripSuffix("\"")}.toSeq
          case ujson.Arr(arr) => 
            // replace double-quotes
            arr.map(v => v.toString.stripPrefix("\"").stripSuffix("\"")).toSeq            
          case _ => 
            // not supported
            Seq()
        }
        r.writeRow(csvValues)
        r.close()
        buf.toString().stripSuffix("\n")
        
      case ss: List[String] => 
        // already 
        ss
      case _ => 
        o.toString        
    }
    
    TextString(csv)
  }

  def fromJson():TextString = {
    val json = o match {
      case s:String => 
        if(s.isBlank) "" else ujson.read(str)

      case jv:ujson.Value.Value => 
        // already
        o
      case ss: List[String] => 
        // incorrect state
        ss
      case _ => ujson.read(str)
    }

    TextString(json)
  }

  def toJson():TextString = {
    val json = o match {
      case s:String => 
        if(s.isBlank()) "" else ujson.write(s)        
      case jv:ujson.Value.Value => ujson.write(jv)
      case ss: List[String] => 
        // convert to obj        
        val kv = ss.view.zipWithIndex.map{case(v,i) => s"v${i}" -> v}.toMap
        ujson.write(kv)
      case _ =>        
        ujson.write(o.toString)
    }    
    TextString(json)
  }
}

object TextString {
  def apply(s:String) = new TextString(s)
  def apply(o:Any) = new TextString(o)
}


import OutputStringJson._
import io.syspulse.skel.serde.Parq._

class PipelineProxy(feed:String,output:String)(implicit config:Config) extends 
      Pipeline[String,TextString,OutputString](
        feed,output,
        config.throttle,
        config.delimiter,
        config.buffer,
        throttleSource = config.throttleSource,
        format = config.format) {
  
  private val log = Logger(s"${this}")
      
  override def getFileLimit():Long = config.limit
  override def getFileSize():Long = config.size

  // deduplication
  def processDedup:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt.str)
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
    }.map(TextString(_))

  def processNone:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt)

  def processFromCsv:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt.fromCsv())

  def processToCsv:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt.toCsv())

  def processFromJson:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt.fromJson())

  def processToJson:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => txt.toJson())

  def processLog:Flow[TextString,TextString,_] = Flow[TextString]
    .map(txt => {
      println(s"${txt}")
      txt
    })
    
  override def process:Flow[String,TextString,_] = {
    val ff = config.params.map(_.toLowerCase match {
      case "dedup" => processDedup
      case "none" | "map" => processNone
      case "log" => processLog
      case "from-json" => processFromJson
      case "to-json" => processToJson
      case "from-csv" => processFromCsv
      case "to-csv" => processToCsv
      case _ => processNone
    }).toList
    
    def pipe(ff:List[Flow[TextString,TextString,_]]):Flow[TextString,TextString,_] = {
      ff.toList match {
        case Nil => processNone
        case f :: Nil => f
        case f :: ff => f.via(pipe(ff))
      }
    }

    val f0 = Flow[String].map(s => TextString(s))
    f0.via(pipe(ff))    
  }
    
  override def parse(data: String): Seq[String] = {
    if(config.delimiter.isEmpty())
      Seq(data)
    else
      data.split(config.delimiter).toSeq
  }

  override def transform(txt: TextString): Seq[OutputString] = {
    //Seq(TextString(s"[${countBytes},${countInput},${countObj},${countOutput}]: ${t}"))
    Seq(OutputString(txt.str))
  }
}
