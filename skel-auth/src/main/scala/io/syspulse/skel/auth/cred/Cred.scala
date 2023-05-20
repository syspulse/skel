package io.syspulse.skel.auth.cred

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class Cred(cid:String, secret:String, name:String, expire: Long, tsCreated:Long, uid:UUID)

object Cred {
  val DEF_AGE = 3600L * 24 * 90// long lived (3 months)
 
  def apply(cid:Option[String], secret:Option[String], name:String="", age: Long = DEF_AGE, uid:UUID ):Cred = {
    val now = System.currentTimeMillis
    new Cred(
      cid.getOrElse(generateClientId()), 
      secret.getOrElse(generateClientSecret()), 
      name, 
      now + age * 1000L, 
      now,
      uid
    )
  }

  def generateClientId() = Util.hex(Util.generateRandom(sz = 16),false)
  def generateClientSecret() = Util.generateRandomToken(sz=32)
}