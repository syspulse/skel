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

import java.time.{ZoneId,ZonedDateTime,LocalDateTime,Instant}
import java.time.format._

object Util {

  val random = new SecureRandom
  val salt: Array[Byte] = Array.fill[Byte](16)(0x1f)
  val digest = MessageDigest.getInstance("SHA-256");  
  
  def toHexString(b:Array[Byte]) = b.foldLeft("")((s,b)=>s + f"$b%02x")
  
  //def hex(x: Seq[Byte],prefix:Boolean=true):String = hex(x.toArray,prefix)
  def hex(x: Array[Byte],prefix:Boolean=true):String = s"""${if(prefix) "0x" else ""}${x.toArray.map("%02x".format(_)).mkString}"""

  def SHA256(data:Array[Byte]):Array[Byte] = digest.digest(data)
  def SHA256(data:String):Array[Byte] = digest.digest(data.getBytes(StandardCharsets.UTF_8))
  def sha256(data:Array[Byte]):String = toHexString(digest.digest(data))
  def sha256(data:String):String = toHexString(digest.digest(data.getBytes(StandardCharsets.UTF_8)))
  
  val tsFormatLongest = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss:SSS")
  val tsFormatLong = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmssZ")
  val tsFormatYM = DateTimeFormatter.ofPattern("yyyy-MM")

  def now:String = tsFormatLongest.format(LocalDateTime.now)
  def now(fmt:String):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Instant.now.toEpochMilli), ZoneId.systemDefault).format(DateTimeFormatter.ofPattern(fmt))
  def timestamp(ts:Long,fmt:String):String = ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault).format(DateTimeFormatter.ofPattern(fmt))

  def tsToString(ts:Long) = ZonedDateTime.ofInstant(
      Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatLong)

  def tsToStringYearMonth(ts:Long = 0L) = ZonedDateTime.ofInstant(
      if(ts==0L) Instant.now else Instant.ofEpochMilli(ts), 
      ZoneId.systemDefault
    ).format(tsFormatYM)
  
  // time is delimited with {}
  def toFileWithTime(fileName:String,ts:Long=System.currentTimeMillis()) = {
    fileName.split("[{}]") match {
      case Array(p,tf,ext) => p + timestamp(ts,tf) + ext
      case Array(p,tf) => p + timestamp(ts,tf)
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

  def getHostPort(address:String):(String,Int) = { 
    val (host,port) = address.split(":").toList match{ 
      case h::p => (h,p(0))
      case _ => (address,"0")
    }
    (host,port.toInt)
  }

  def rnd(limit:Double) = Random.between(0,limit)

  def toCSV(o:Product):String = o.productIterator.foldRight("")(_.toString + "," + _.toString).stripSuffix(",")

  import scala.reflect.runtime.universe._ 

  // this does not work and needs type tags information
  def isCaseClass(v: Any): Boolean = {
     val typeMirror = runtimeMirror(v.getClass.getClassLoader)
     val instanceMirror = typeMirror.reflect(v)
     val symbol = instanceMirror.symbol
     symbol.isCaseClass
  }

  protected def traverseAny(a:Any):Array[(String,String)] = {
    val ff = a.getClass.getDeclaredFields.map( v => (v.getName,v))
    ff.map { case(n,f) => {
      f.setAccessible(true)
      val typeName = f.getGenericType.getTypeName.toString
      if(typeName.startsWith("scala.collection")) {
        val o = f.get(a)
        val vv:Array[(String,String)] = o.asInstanceOf[Seq[_]].map(v => traverseAny(v)).toArray.flatten
        vv
      } else {
        val v = f.get(a)
        if(v!=null 
          && !v.getClass.isPrimitive 
          && !v.isInstanceOf[java.lang.Byte]
          && !v.isInstanceOf[java.lang.Integer]
          && !v.isInstanceOf[java.lang.Long]
          && !v.isInstanceOf[java.lang.Short]
          && !v.isInstanceOf[java.lang.Boolean]
          && !v.isInstanceOf[java.lang.Double]
          && !v.isInstanceOf[java.lang.String])
          traverseAny(v)
        else
          Array[(String,String)]((n,if(v!=null) v.toString else "null"))
      }
    }}.flatten
  }
  
  
  def toFlatData(o:Product,sep:String=":"):String = {
    val mm = traverseAny(o)
    mm.foldRight("")(_._2 + sep + _).stripSuffix(sep)
  }

  def getDirWithSlash(dir:String):String = if(dir.isBlank()) dir else if(dir.trim.endsWith("/")) dir else dir + "/"
}


