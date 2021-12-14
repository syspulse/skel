package io.syspulse.skel.crypto

import scala.jdk.CollectionConverters

object key {
  type Signature = String
  type PK = String
  type SK = String
} 

import key._

trait KeyPair

case class KeyECDSA(sk:SK,pk:PK) extends KeyPair
case class KeyBLS(sk:SK,pk:PK) extends KeyPair

object Key {

}


