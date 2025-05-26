package io.syspulse.skel.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._

import io.syspulse.skel.util.Util

class Addr(addr0:String,chain0:Option[String]) {
  val addr = addr0.trim.toLowerCase
  val chain = chain0.map(_.trim.toLowerCase)

  override def toString = if(chain.isDefined) s"${chain.get}:${addr}" else addr
}

object Addr {
  def apply(addr:String,chain:Option[String]):Addr = new Addr(addr,chain)
  def apply(addr0:String):Addr = {
    val (addr,chain) = normalize(addr0)
    new Addr(addr,chain)
  }

  def normalize(addr0:String):(String,Option[String]) = {
    val addr = addr0.trim.toLowerCase
    val i = addr.indexOf(":")
    if(i >= 0) {
      (addr.substring(i+1),Some(addr.substring(0,i)))
    } else {
      (addr,None)
    }
  }
}
