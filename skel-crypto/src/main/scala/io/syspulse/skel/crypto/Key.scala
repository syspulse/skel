package io.syspulse.skel.crypto

import scala.jdk.CollectionConverters

import org.web3j.utils.{Numeric}

import io.syspulse.skel.util.Util

object key {
  type Signature = String
  type SK = Array[Byte]
  type PK = Array[Byte]
} 

import key._

abstract class KeyPair {
  def sk:SK
  def pk:PK
  override def toString = s"${this.getClass.getSimpleName}(${Util.hex(sk)},${Util.hex(pk)})"
}

case class KeyECDSA(sk:SK,pk:PK) extends KeyPair
case class KeyBLS(sk:SK,pk:PK) extends KeyPair

object SK {
  def apply(sk:String) = Numeric.hexStringToByteArray(sk)
}

object PK {
  def apply(pk:String) = Numeric.hexStringToByteArray(pk)
}


