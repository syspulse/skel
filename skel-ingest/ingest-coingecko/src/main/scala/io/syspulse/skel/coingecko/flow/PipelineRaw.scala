package io.syspulse.skel.coingecko.flow

import scala.annotation.tailrec

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import spray.json._

import akka.actor.ActorSystem
import akka.util.ByteString
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.flow.Pipeline
import io.syspulse.skel.Ingestable

import io.syspulse.skel.serde.ParqIgnore
import com.github.mjakubowski84.parquet4s.ParquetRecordEncoder
import com.github.mjakubowski84.parquet4s.ParquetSchemaResolver

import io.syspulse.skel.coingecko.Config
import io.syspulse.skel.coingecko.Coingecko
import scala.concurrent.Future

import io.syspulse.skel.coingecko.CoingeckoJson
import io.syspulse.skel.coingecko.{Coingecko_Coin,Coingecko_CoinData}

case class StringableJson(js:String,name:String) extends Ingestable {
  // for new file name
  override def getId:Option[String] = Some(name)  
  override def toLog:String = js  

  // because it is a wrapped json, it must be printed as json
  // need to add new line
  override def toString = s"${js}\n"
}

object StringableJson extends DefaultJsonProtocol {
  implicit object StringableJsonFormat extends RootJsonFormat[StringableJson] {
    def write(s: StringableJson) = JsString(s.js)
    def read(value: JsValue) = value match {
      case JsString(str) => StringableJson(str,"")
      case _ => deserializationError("plain text expected")
    }
  }
  implicit val fmt = jsonFormat2(StringableJson.apply _)
}
import StringableJson._


class PipelineRawCoins(feed:String,output:String)(implicit config:Config) extends 
      PipelineCoingecko[String,JsValue,StringableJson](feed, output,config) {
  
  import CoingeckoJson._

  // data is a list of array of coin id objects
  override def parse(data: String): Seq[String] = {
    if(data.isBlank()) 
      return Seq.empty
    
    val coins = data.parseJson.convertTo[Seq[Coingecko_Coin]]
    log.info(s"[${sid()}] coins=${coins.size}")
    coins.map(_.id)
  }

  // process every coin
  override def process:Flow[String,JsValue,_] = {    
    Flow[String]
      .filter( id => {
        config.filter.isEmpty || config.filter.contains(id)
      })
      .throttle(1,FiniteDuration(config.throttle,TimeUnit.MILLISECONDS))
      .mapAsync(1)(id  => {
        coingecko.get.askCoinsAsync(Set(id))
        // Future.successful(JsObject(
        //   "id" -> JsString(id),
        //   "symbol" -> JsString(id),
        //   "name" -> JsString(id),
        // ))
        .recoverWith {
          case e: Exception =>
            // Log the error
            log.warn(s"Failed: id=${id}: ${e.getMessage}")
            // Return a failed future to trigger restart
            akka.pattern.after(FiniteDuration(config.retryDelay,TimeUnit.MILLISECONDS), system.scheduler) {
              Future.failed(e)
            }
        }
      })      
  }
  
  override def transform(js: JsValue): Seq[StringableJson] = { 
    val oid = js.asJsObject.fields.get("id").getOrElse("").toString.stripPrefix("\"").stripSuffix("\"")
    val output = js.compactPrint
    log.info(s"=> ${oid} (${output.size} bytes)")
    Seq(StringableJson(output,oid))
  }
}

class PipelineRawCoin(feed:String,output:String)(implicit config:Config) extends 
      PipelineCoingecko[String,(String,String),StringableJson](feed, output,config) {
  
  import CoingeckoJson._

  // coin data one-liners
  override def parse(data: String): Seq[String] = {    
    data.split("\n").filter(!_.isBlank()).toSeq
  }

  // process every coin
  override def process:Flow[String,(String,String),_] = {
    Flow[String]      
      .map(data  => {  
        data.parseJson.convertTo[Coingecko_CoinData]
      })
      .filter( c => 
        config.filter.isEmpty || config.filter.contains(c.id)
      )
      .map(c => (c.id,c.toJson.compactPrint))
  }
  
  override def transform(o: (String,String)): Seq[StringableJson] = { 
    val (oid,output) = o
    log.info(s"=> ${oid} (${output.size} bytes)")
    Seq(StringableJson(output,oid))
  }
}
