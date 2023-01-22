package io.syspulse.skel.auth.cid

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Client(cid:String, secret:String, name:String, expire: Long, tsCreated:Long)

object Client {
  val DEF_AGE = 3600L * 24 * 90// long lived (3 months)
 
  def apply(cid:Option[String], secret:Option[String], name:String="", age: Long = DEF_AGE):Client = {
    val now = System.currentTimeMillis
    new Client(cid.getOrElse(generateClientId()), secret.getOrElse(generateClientSecret()), name, now + age * 1000L, now)
  }

  def generateClientId() = Util.hex(Util.generateRandom(sz = 16),false)
  def generateClientSecret() = Util.generateRandomToken(sz=32)
}

