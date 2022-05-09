package io.syspulse.skel.stream.eth

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, IOResult, Materializer}
import akka.stream.scaladsl.{Sink, Source, StreamConverters,Flow}
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

import com.typesafe.scalalogging.Logger

// ATTENTION
import io.syspulse.skel.service.JsonCommon
import spray.json._
import spray.json.DefaultJsonProtocol

import io.syspulse.skel.stream.StreamStd
import io.syspulse.skel.stream.Config
import io.syspulse.skel.dsl.JS

case class Tx(
  ts:Long,
  txIndex:Int,
  hash:String,
  blockNumber:Long,
  fromAddress:String,
  toAddress:String,
  gas:Long,
  gasPrice:BigInt,
  intput:String,
  value:BigInt,
)

object EthJson extends JsonCommon {
  import DefaultJsonProtocol._
  implicit val txJsonFormat = jsonFormat(Tx,"block_timestamp","transaction_index","hash","block_number","from_address","to_address","gas","gas_price","input","value")
}


class StreamEthTx() extends StreamStd {
  import EthJson._
  import DefaultJsonProtocol._

  //override val outSink = Sink.ignore

  override def preProc(data:String):String = {
    val tx = data.parseJson.convertTo[Tx]
    
    val r = if(js.isDefined) js.get.run(Map( ("from_address" -> tx.fromAddress) )).toString else ""

    log.info(s"tx: ${tx}: ${Console.YELLOW}${r}${Console.RESET}")
    r
  }
}