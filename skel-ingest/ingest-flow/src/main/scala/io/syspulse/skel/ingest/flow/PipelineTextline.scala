package io.syspulse.skel.ingest.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

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
import io.syspulse.skel.ingest.store._

import spray.json._
import java.util.concurrent.TimeUnit

case class Textline(txt:String) extends skel.Ingestable {
  override def toString = txt
}


// customr Protocol to print embedded json in txt
trait TextlineJsonProtocol extends DefaultJsonProtocol {
  private val log = Logger(s"${this}")

  implicit object TextlineJsonFormat extends RootJsonFormat[Textline] {
    def write(t: Textline) = {
      val ast = t.txt.parseJson
      ast
    }

    def read(value: JsValue) = value match {
      case JsString(str) => Textline(str)
      case _ => deserializationError("plain text expected")
    }
  }

  implicit val fmt = jsonFormat1(Textline.apply _)
}

object TextlineJson extends TextlineJsonProtocol { 
}

import TextlineJson._

class PipelineTextline(feed:String,output:String)(implicit config:Config) extends 
      Pipeline[String,String,Textline](feed,output,config.throttle,config.delimiter,config.buffer) {
  
  private val log = Logger(s"${this}")
      
  // Jsoners are needed for ElasticSink !
  //import TextlineJson._
  
  //import TextlineJsonProtocol._
  //override val fmt:JsonFormat[Textline] = TextlineJson.fmt

  override def processing:Flow[String,String,_] = Flow[String].map(s => s)
  def parse(data: String): Seq[String] = {
    data.split("\n").toSeq
  }
  def transform(txt: String): Seq[Textline] = {
    //Seq(Textline(s"[${countBytes},${countInput},${countObj},${countOutput}]: ${t}"))
    val t = Textline(txt)
    //log.info(s"json=${t.toJson.prettyPrint}")
    //t.toJson.prettyPrint
    Seq(t)
  }
}