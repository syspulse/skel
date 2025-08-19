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

case class StringWrap(s:String,name:String) extends Ingestable {
  // for new file name
  override def getId:Option[String] = Some(name)  
  override def toLog:String = s

  // because it is a wrapped json, it must be printed as json
  // need to add new line
  override def toString = if(s.endsWith("\n")) s else s + "\n"
}

object StringWrap extends DefaultJsonProtocol {
  implicit object StringWrapFormat extends RootJsonFormat[StringWrap] {
    def write(s: StringWrap) = JsString(s.s)
    def read(value: JsValue) = value match {
      case JsString(str) => StringWrap(str,"")
      case _ => deserializationError("plain text expected")
    }
  }
  implicit val fmt = jsonFormat2(StringWrap.apply _)
}

