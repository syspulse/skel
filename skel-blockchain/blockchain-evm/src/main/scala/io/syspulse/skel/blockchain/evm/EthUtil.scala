package io.syspulse.skel.blockchain.eth

import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

object EthUtil {
  
  def decodeERC20Transfer(data:String,topics:Array[String]) = {
    if(topics.size > 0 && topics(0) == "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef") {
      // standard is to have 2 values in topic
      topics.size match {
        case 3 => 
          // https://etherscan.io/tx/0xe8b8eb76d5a102706045a64e4efee8dc40337badc2763caca074237ac28d825f#eventlog
          val from = s"0x${topics(1).drop(24 + 2)}".toLowerCase()
          val to = s"0x${topics(2).drop(24 + 2)}".toLowerCase()
          val v = EthUtil.toBigInt(data)
          Some((from,to,v))
        case 4 =>
          // transfer(address,uint256)          
          val from = s"0x${topics(1).drop(24 + 2)}".toLowerCase()
          val to = s"0x${topics(2).drop(24 + 2)}".toLowerCase()
          val v = EthUtil.toBigInt(s"0x${topics(3).drop(2)}".toLowerCase())
          Some((from,to,v))
        case 1 =>
          // CrytpKitty style (https://etherscan.io/tx/0x9514ca69668169270225a1d2d713dee6aa3fc797107d1d710d4d9c622bfcc3bb#eventlog)
          val from = s"0x${data.drop(2 + 24)}".toLowerCase()
          val to = s"0x${data.drop(2 + 32 * 2 + 24 * 2)}".toLowerCase()
          val v = s"0x${data.drop(2 + 32 * 2 + 32 * 2 + 24 * 2)}" 
          Some((from,to,v))
      }
    } else 
      None
  }

  def toLong(data:Option[String]) = data.map(data =>java.lang.Long.parseLong(data.stripPrefix("0x"),16))
  def toLong(data:String) = java.lang.Long.parseLong(data.stripPrefix("0x"),16)

  def toBigInt(data:Option[String]) = data.map(data => Util.toBigInt(data)) //BigInt(Util.unhex(data))
  def toBigInt(data:String) = Util.toBigInt(data) //BigInt(Util.unhex(data))
  
  def toOption(data:String) = if(data.isEmpty() || data=="0x") None else Some(data)
  def toOptionLong(data:String) = if(data.isEmpty() || data=="0x") None else Some(toLong(data))
}
