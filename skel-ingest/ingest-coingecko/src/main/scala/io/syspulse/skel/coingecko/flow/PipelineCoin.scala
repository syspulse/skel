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

import CoinsJson._

class PipelineCoin(feed:String,output:String)(implicit config:Config) extends 
      PipelineCoingecko[String,Coin,Coin](feed, output,config) {

  import CoingeckoJson._
  
  // this is already a single coin
  override def parse(data: String): Seq[String] = {
    if(data.isBlank()) return Seq.empty

    Seq(data)
  }

  // process every coin
  override def process:Flow[String,Coin,_] = {    
    Flow[String]
      .map(data => {
        parseCoinData(data)
      })
      .filter( c => c.isDefined)
      .map( c => c.get )
      .filter( c => {
        config.filter.isEmpty || config.filter.contains(c.id)
      })      
      .map(c => {
        val tokens:Map[String,Token] = if(! c.platforms.isDefined) 
          Map.empty 
        else {
          c.platforms.get.flatMap{ case(bid,addr) => 
            if(addr.isBlank()) 
              None 
            else {
              val dec = c.detail_platforms(bid).decimal_place.getOrElse(18)
              Some(Token(
                bid = bid,
                sym = c.symbol,
                addr = addr,              
                dec = dec,              
              ))
            }
          }
          .map(t => t.bid -> t)
          .toMap
        }

        Coin(
          sym = c.symbol,         
          tokens = tokens, 
          icon = Some(c.image.large),
          sid = Some("cg"),
          xid = Some(c.id),
        )
      })
  }
  
  override def transform(coin: Coin): Seq[Coin] = {    
    Seq(coin)
  }
}
