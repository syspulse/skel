package io.syspulse.skel.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._

import io.syspulse.skel.util.Util

class Addr(addr0:String,chain0:Option[String]=None) {
  val (addr,chain) = Addr.normalize(addr0,chain0)  

  override def toString = if(chain.isDefined) s"${chain.get}:${addr}" else addr

  def ==(other: Addr): Boolean = Addr.==(this, other)
}

object Addr {
  def apply(addr:String,chain:Option[String]):Addr = new Addr(addr,chain)
  def apply(addr:String):Addr = new Addr(addr,None)

  def normalize(addr0:String,chain0:Option[String]=None):(String,Option[String]) = {
    val addr = addr0.trim
    val i = addr.indexOf(":")
    
    val (addr1,chain1) = if(i >= 0) {
      (addr.substring(i+1),Some(addr.substring(0,i)))
    } else {
      (addr,chain0)
    }

    val chain2 = chain1.map(_.trim.toLowerCase)

    val addr2 =
      if(addr1.startsWith("0x") || addr1.startsWith("0X"))
        addr1.toLowerCase
      else if(chain2.contains(Blockchain.STELLAR.name))
        addr1.toUpperCase
      else
        addr1

    val chain3 =
      if(chain2.isDefined && isEvm(addr2) && chain2.flatMap(blockchains.get).isDefined)
        Some(Blockchain.EVM.name)
      else
        chain2

    (addr2,chain3)
  }

  def shorten(addr:String):String = Util.trunc(addr,12)

  private val blockchains = Blockchain.ALL_NAMES

  private def isHex(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')

  private def isEvm(addr: String): Boolean = {
    addr.length == 42 && (addr.startsWith("0x") || addr.startsWith("0X"))
  }

  /** Compare two addresses for semantic equality.
    *
    * - Chain is compared when both sides specify it; all EVM chains are treated as identical (`base` == `ethereum`).
    * - Address casing rules:
    *   - EVM + Starknet hex addresses are case-insensitive
    *   - Solana/Tron/Bitcoin are case-sensitive
    *   - Stellar StrKey is case-insensitive (base32; typically uppercase)
    */
  def ==(a: Addr, b: Addr): Boolean = {
    val chainOk =
      (a.chain, b.chain) match {
        case (Some(x), Some(y)) =>
          x == y
        case _ =>
          // if any side doesn't specify chain, compare by address only
          true
      }

    if(!chainOk) return false

    a.addr == b.addr
  }

  def ==(a: String, b: String): Boolean = ==(Addr(a), Addr(b))
}
