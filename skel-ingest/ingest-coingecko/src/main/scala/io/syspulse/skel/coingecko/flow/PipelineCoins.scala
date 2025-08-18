package io.syspulse.skel.coingecko.flow

import scala.annotation.tailrec

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger

import spray.json._

import akka.util.ByteString
import akka.stream.scaladsl.Flow

import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.flow.Pipeline
import io.syspulse.skel.Ingestable

import io.syspulse.skel.serde.Parq._
import com.github.mjakubowski84.parquet4s.ParquetRecordEncoder
import com.github.mjakubowski84.parquet4s.ParquetSchemaResolver
import akka.actor.ActorSystem

import io.syspulse.skel.blockchain.Coin
import io.syspulse.skel.blockchain.Token

import io.syspulse.skel.coingecko.Config
import io.syspulse.skel.coingecko.CoingeckoJson
import io.syspulse.skel.coingecko.Coingecko
import io.syspulse.skel.coingecko.{Coingecko_Coin,Coingecko_CoinData}
import scala.concurrent.Future

object CoinsJson extends DefaultJsonProtocol {
  implicit val js_Token = jsonFormat6(Token)
  implicit val js_Coin = jsonFormat7(Coin)
}
import CoinsJson._

class PipelineCoins(feed:String,output:String)(implicit config:Config) extends 
      PipelineCoingecko[String,Coin,Coin](feed, output,config) {

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
  override def process:Flow[String,Coin,_] = {    
    Flow[String]
      .filter( id => {
        config.filter.isEmpty || config.filter.contains(id)
      })
      .throttled(1,FiniteDuration(config.throttle,TimeUnit.MILLISECONDS))
      .mapAsync(1)(id  => {
        
        //coingecko.get.askCoinsAsync(Set(id))
        coingecko.get.requestCoins(Set(id))
        
        // Future.successful(JsObject(
        //   "id" -> JsString(id),
        //   "symbol" -> JsString(id),
        //   "name" -> JsString(id),
        // ))
        .recoverWith {
          case e: Exception =>
            // Log the error
            log.warn(s"Failed to get coin: id=${id}: ${e.getMessage}")
            // Return a failed future to trigger restart
            akka.pattern.after(FiniteDuration(config.retryDelay,TimeUnit.MILLISECONDS), system.scheduler) {
              Future.failed(e)
            }
        }
      })      
      .map(c => {
        Coingecko.parseCoinData(c,config.parser)
      })
      .filter(c => c.isDefined)
      .map(c => c.get)
      .map(c => {
        Coingecko.toCoin(c)
      })      
  }
  
  override def transform(coin: Coin): Seq[Coin] = {    
    Seq(coin)
  }
}
