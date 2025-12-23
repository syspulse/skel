package io.syspulse.crypto.eth

import io.syspulse.skel.Ingestable

case class Tx(
  ts:Long,
  txIndex:Int,
  hash:String,
  blockNumber:Long,
  fromAddress:String,
  toAddress:Option[String],
  gas:Long,
  gasPrice:BigInt,
  input:String,
  value:BigInt,
) extends Ingestable {
  override def getKey:Option[Any] = Some(hash)
}
