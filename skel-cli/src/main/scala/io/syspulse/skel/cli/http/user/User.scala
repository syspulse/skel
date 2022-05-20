package io.syspulse.skel.cli.user

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.crypto.wallet.Signer

case class User(userId:UUID,name:String, email:String, signers:List[Signer] = List(),var nonce:Long=0L, var accessToken:String = "", var accessTokenTime:Long = 0L) {

  override def toString = s"User(${userId},${name},${email},${signers},${nonce},${accessToken},${accessTokenTime}"

  def getAddr(pk:String) = Eth.address(pk)
}
