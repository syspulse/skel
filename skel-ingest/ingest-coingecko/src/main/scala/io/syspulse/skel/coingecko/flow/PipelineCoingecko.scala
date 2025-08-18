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

  implicit class FlowOps[T, U, M](flow: Flow[T, U, M]) {
    def throttled(rate: Int, per: FiniteDuration): Flow[T, U, M] = {
      if (per.toMillis > 0) 
        flow.throttle(rate, per) 
      else 
        flow      
    }
  }

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
  
}
