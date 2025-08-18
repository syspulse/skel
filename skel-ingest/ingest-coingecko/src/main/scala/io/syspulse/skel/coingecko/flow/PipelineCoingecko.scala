package io.syspulse.skel.coingecko.flow

import scala.annotation.tailrec

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.TimeUnit
import com.typesafe.scalalogging.Logger
import scala.concurrent.Future

import spray.json._

import akka.actor.ActorSystem
import akka.util.ByteString
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel.serde.Parq._
import com.github.mjakubowski84.parquet4s.ParquetRecordEncoder
import com.github.mjakubowski84.parquet4s.ParquetSchemaResolver

import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.flow.Pipeline
import io.syspulse.skel.Ingestable

import io.syspulse.skel.coingecko.Config
import io.syspulse.skel.coingecko._
import io.syspulse.skel.coingecko.CoingeckoJson

abstract class PipelineCoingecko[I,T,O <: Ingestable](feed:String,output:String,config:Config)
  (implicit fmt:JsonFormat[O],parqEncoders:ParquetRecordEncoder[O],parsResolver:ParquetSchemaResolver[O],as:Option[ActorSystem] = None) extends 
      Pipeline[I,T,O](
        feed, 
        output,
        config.throttle,
        config.delimiter,
        config.buffer,
        throttleSource = config.throttleSource,
        format = config.format) {

  val log = Logger(s"${this}")  
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  import CoingeckoJson._

  @volatile
  var coingecko:Option[Coingecko] = None

  def sid() = "coingecko"

  override def source(feed:String):Source[ByteString,_] = {    
    feed.split("://").toList match {
      case ("coingecko" | "cg") :: _ => 
        val (cg,src) = Coingecko.fromCoingecko(feed)
        coingecko = Some(cg)
        src
      case _ => 
        super.source(feed)
    }    
  }

  def getRawId(json:String):String = {
    Util.walkJson(json,".id").getOrElse(Seq()).headOption.map(_.toString).getOrElse("not found")
  }

  def parseCoinData(json:String):Option[Coingecko_CoinData] = {
    try {
      config.parser match {
        case "ujson" =>
          val u = ujson.read(json)
          
          // Extract basic fields
          val id = u.obj.get("id").map(_.str).getOrElse("")
          val name = u.obj.get("name").map(_.str).getOrElse("")
          val symbol = u.obj.get("symbol").map(_.str).getOrElse("")
          
          // Extract optional fields with defaults          
          val assetPlatformId = u.obj.get("asset_platform_id").map(_.str)
          val platforms = u.obj.get("platforms").map(_.obj.map { case (k, v) => k -> v.str }.toMap)
          
          val lastUpdated = u.obj.get("last_updated").map(_.str).getOrElse("")
                              
          val image = Coingecko_Image(
            thumb = u.obj.get("image").flatMap(_.obj.get("thumb")).map(_.str).getOrElse(""),
            small = u.obj.get("image").flatMap(_.obj.get("small")).map(_.str).getOrElse(""),
            large = u.obj.get("image").flatMap(_.obj.get("large")).map(_.str).getOrElse("")
          )
          
          // Create detail platforms map
          val detailPlatforms = u.obj.get("detail_platforms").map(_.obj.map { case (k, v) =>
            k -> Coingecko_Platform(
              decimal_place = v.obj.get("decimal_place").map(_.num.toInt),
              contract_address = v.obj.get("contract_address").map(_.str).getOrElse("")
            )
          }.toMap).getOrElse(Map.empty)
          
          val c = Coingecko_CoinData(
            id = id,
            symbol = symbol,
            name = name,
            web_slug = None, // Not extracted
            asset_platform_id = assetPlatformId,
            platforms = platforms,
            detail_platforms = detailPlatforms,
            block_time_in_minutes = None, // Not extracted
            hashing_algorithm = None, // Not extracted
            categories = Seq.empty, // Not extracted
            preview_listing = false, // Not extracted
            public_notice = None, // Not extracted
            additional_notices = Seq.empty, // Not extracted
            localization = null, // Not needed
            description = null, // Not needed
            links = null, // Not needed
            image = image,
            country_origin = "", // Not extracted
            genesis_date = None, // Not extracted
            sentiment_votes_up_percentage = None, // Not extracted
            sentiment_votes_down_percentage = None, // Not extracted
            watchlist_portfolio_users = 0, // Not extracted
            market_cap_rank = 0, // Not extracted
            market_data = null, // Not needed
            community_data = null, // Not needed
            developer_data = null, // Not needed
            status_updates = Seq.empty, // Not extracted
            last_updated = lastUpdated,
            tickers = Seq.empty // Not extracted
          )

          Some(c)
        case "json" =>
          Some(json.parseJson.convertTo[Coingecko_CoinData])
      }
    } catch {
      case e: Exception =>
        log.warn(s"[${sid()}] failed to parse: id=${getRawId(json)}: '${Util.trunc(json,64)}': ${e.getMessage}")
        None
    }
  }
}
