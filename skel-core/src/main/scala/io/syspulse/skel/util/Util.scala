package io.syspulse.skel.util

import java.time._
import java.time.format._
import java.time.temporal._
import java.util.Locale
import io.jvm.uuid._

import scala.util.Random

import java.security.SecureRandom
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import java.time.{ZoneId,ZonedDateTime,Instant}
import java.time.format._

object Util {

  val random = new SecureRandom
  val salt: Array[Byte] = Array.fill[Byte](16)(0x1f)
  val digest = MessageDigest.getInstance("SHA-256");  
  
  def toHexString(b:Array[Byte]) = b.foldLeft("")((s,b)=>s + f"$b%02x")

  def SHA256(data:Array[Byte]):Array[Byte] = digest.digest(data)
  def SHA256(data:String):Array[Byte] = digest.digest(data.getBytes(StandardCharsets.UTF_8))
  def sha256(data:Array[Byte]):String = toHexString(digest.digest(data))
  def sha256(data:String):String = toHexString(digest.digest(data.getBytes(StandardCharsets.UTF_8)))
  
  val tsFormatLongest = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSS")
  val tsFormatLong = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ")
  val tsFormatYM = DateTimeFormatter.ofPattern("yyyy-MM")

  def now:String = tsFormatLongest.format(LocalDateTime.now)
  def now(fmt:String):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Instant.now.toEpochMilli), ZoneId.systemDefault).format(DateTimeFormatter.ofPattern(fmt))
  
  def tsToString(ts:Long) = ZonedDateTime.ofInstant(
      Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatLong)

  def tsToStringYearMonth(ts:Long = 0L) = ZonedDateTime.ofInstant(
      if(ts==0L) Instant.now else Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatYM)
  
  // time is delimited with {}
  def toFileWithTime(fileName:String) = {
    fileName.split("[{}]") match {
      case Array(p,ts,ext) => p + now(ts) + ext
      case Array(p,ts) => p+now(ts)
      case Array(p) => p
      case Array() => fileName
    }
  }

  def info = {
    val p = getClass.getPackage
    val name = p.getImplementationTitle
    val version = p.getImplementationVersion
    (name,version)
  }

  def uuid(id:String,entityName:String=""):UUID = {
    val bb = Util.SHA256(entityName).take(4) ++  Array.fill[Byte](2+2+2)(0) ++ Util.SHA256(id).take(6)
    UUID(bb)
  }

  def getHostPort(address:String):(String,Int) = { val (host,port) = address.split(":").toList match{ case h::p => (h,p(0))}; (host,port.toInt)}

  def rnd(limit:Double) = Random.between(0,limit)
}


