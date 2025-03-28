package io.syspulse.skel.blockchain.eth

import java.util.concurrent.atomic.AtomicLong
import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

object EthUtil {

  val EVENT_SWAP = "0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822"
  val EVENT_SWAP_V4 = "0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67"
  val EVENT_TRANSFER = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"

  case class Swap(sender:String,to:String,amount0In:BigInt,amount1In:BigInt,amount0Out:BigInt,amount1Out:BigInt)

  def hexToSignedBigInt(hex: String): BigInt = {
    val unsigned = BigInt(hex, 16)
    if ((unsigned & (BigInt(1) << (hex.length * 4 - 1))) != 0) {  // check if negative
      unsigned - (BigInt(1) << (hex.length * 4))
    } else {
      unsigned
    }
  }

  def decodeSwap(data:String,topics:Array[String]) = {
    if(topics.size > 0) {
      if(topics(0) == EVENT_SWAP) {
        topics.size match {
          case 3 =>             
            val sender = s"0x${topics(1).drop(24 + 2)}".toLowerCase()
            val to = s"0x${topics(2).drop(24 + 2)}".toLowerCase()
            val swapParams = data.drop(2).grouped(64).toSeq.map(v=>BigInt(v,16))
            Some(Swap(sender,to,swapParams(0),swapParams(1),swapParams(2),swapParams(3)))

          case _ => None
        }
      } 
      else 
      if(topics(0) == EVENT_SWAP_V4) {
        topics.size match {
          case 3 =>               
            val sender = s"0x${topics(1).drop(24 + 2)}".toLowerCase()
            val to = s"0x${topics(2).drop(24 + 2)}".toLowerCase()
            val swapParams = data.drop(2).grouped(64).toSeq.map(v=>hexToSignedBigInt(v))
            val amount0 = swapParams(0)
            // negate the amount for what user receives
            val amount1 = -swapParams(1)
            Some(Swap(sender,to,0,amount0,amount1,0))

          case _ => None
        }
      } else 
        None
    } else 
      None
  }
  
  def decodeTransfer(data:String,topics:Array[String]) = {
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
          val v = EthUtil.toBigInt(s"0x${topics(3).drop(2)}")
          Some((from,to,v))
        case 1 =>
          // CrytpKitty style (https://etherscan.io/tx/0x9514ca69668169270225a1d2d713dee6aa3fc797107d1d710d4d9c622bfcc3bb#eventlog)
          val from = s"0x${data.drop(2 + 24)}".toLowerCase()
          val to = s"0x${data.drop(2 + 32 * 2 + 24 * 2)}".toLowerCase()
          val v = EthUtil.toBigInt(s"0x${data.drop(2 + 32 * 2 + 32 * 2 + 24 * 2)}")
          Some((from,to,v))
        case _ => None
      }
  }

  def decodeERC20Transfer(data:String,topics:Array[String]) = {
    if(topics.size > 0 && topics(0) == EVENT_TRANSFER) {
      // standard is to have 2 values in topic
      decodeTransfer(data,topics)
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
