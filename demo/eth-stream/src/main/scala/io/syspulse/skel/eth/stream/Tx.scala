package io.syspulse.skel.eth.stream

import com.typesafe.scalalogging.Logger

// ATTENTION
import io.syspulse.skel.service.JsonCommon
import spray.json._
import spray.json.{DefaultJsonProtocol,NullOptions}


case class Tx(
  ts:Long,
  txIndex:Int,
  hash:String,
  blockNumber:Long,
  fromAddress:String,
  toAddress:Option[String],
  gas:Long,
  gasPrice:BigInt,
  intput:String,
  value:BigInt,
)

object EthJson extends JsonCommon with NullOptions {
  import DefaultJsonProtocol._
  implicit val txJsonFormat = jsonFormat(Tx,"block_timestamp","transaction_index","hash","block_number","from_address","to_address","gas","gas_price","input","value")
}
