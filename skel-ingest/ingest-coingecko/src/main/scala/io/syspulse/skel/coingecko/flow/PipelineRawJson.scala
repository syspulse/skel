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

import StringWrap._


// --- Uses Json to validate ---------------------------------------------------------------------------------
class PipelineRawCoinsJson(feed:String,output:String)(implicit config:Config) extends 
      PipelineCoingecko[String,JsValue,StringWrap](feed, output,config) {
  
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
      .throttled(1,FiniteDuration(config.throttle,TimeUnit.MILLISECONDS))
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
  
  override def transform(js: JsValue): Seq[StringWrap] = { 
    val oid = js.asJsObject.fields.get("id").getOrElse("").toString.stripPrefix("\"").stripSuffix("\"")
    val output = js.compactPrint
    log.info(s"=> ${oid} (${output.size} bytes)")
    Seq(StringWrap(output,oid))
  }
}
