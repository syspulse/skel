package io.syspulse.skel.scrap.npp

import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model._
import java.time.format.DateTimeFormatter
import java.time.ZonedDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime

case class Radiation(ts:Long,area:String,lat:Double,lon:Double,dose:Double)

class NppScrap(rootUrl:String = "http://localhost:30004/MEDO-PS", limit:Int = Int.MaxValue, delay:Long = 1000L) {
  
  val tsFormat = DateTimeFormatter.ofPattern("dd.MM.yyyyHH:mm")
  val nppZoneId = ZoneId.of("Europe/Kiev")
  
  def decodeTsToZone(date:String,time:String):ZonedDateTime = {
    val dt = tsFormat.parse(s"${date}${time}")  
    val zdt = LocalDateTime.from(dt).atZone(nppZoneId)
    zdt
  }

  def decodeTs(date:String,time:String):Long = {
    val zdt = decodeTsToZone(date,time)
    zdt.toInstant.toEpochMilli
  }

  def decodeLat(latitude:String):Double = {
    if(latitude==null || latitude.trim.size <1 ) return 0.0
    val lat = latitude.trim.toUpperCase
    lat.charAt(0) match {
      case 'N' => lat.stripPrefix("N").toDouble
      case 'S' => -lat.stripPrefix("S").toDouble
      case _ => lat.toDouble
    }
  }

  def decodeLon(longitude:String):Double = {
    if(longitude==null || longitude.trim.size <1 ) return 0.0
    val lon = longitude.trim.toUpperCase
    lon.charAt(0) match {
      case 'E' => lon.stripPrefix("E").toDouble
      case 'W' => -lon.stripPrefix("W").toDouble
      case _ => lon.toDouble
    }
  }

  def decodeDose(dose:String):Double = {
    if(dose == null | dose.trim.size < 1) return 0.0

    dose.trim.split("\\s+") match {
      case Array(v,"nSv/h") => v.toDouble
      case Array(v,"mSv/h") => v.toDouble * 1000.0
      case Array(v,"miSv/h") => v.toDouble * 1000.0 * 1000.0
      case Array(v,_) => v.toDouble
      case Array(v) => v.toDouble
      case _ => 0.0
    }
  }

  def scrap() = {
    def sensorUrl(href:String) = s"${rootUrl}/${href}"

    val browser = JsoupBrowser()

    val doc = browser.get(s"${rootUrl}/index.php?online=1")

    // (doc >> extractor("area")).map(e => (e.attr("coords"),e.attr("alt")))
    // def ~~(s:String) = s.stripPrefix("\'").stripSuffix("\'")
    // def ~~!(s:String) = s.stripPrefix("\'").stripSuffix("\');")
    // def parse(href:String) = href.split(",") match { case Array(_,_,station,v1,v2,v3,v4) => (~~(v1),~~(v2),~~(v3),~~!(v4)) }

    def getUrl(s:String) = s.stripPrefix("javascript:popup('")
    val sensorData = (doc >> extractor("area")).map(e => (e.attr("alt"),getUrl(e.attr("href"))))

    sensorData.take(limit).map( data =>{
      val docSensor = browser.get(sensorUrl(data._2))
      val dataMap = (docSensor >> extractor("tr")).map(_.extract("td").map(_.text)).map( v=> v.head.trim -> v.tail.head.trim).toMap

      if(delay > 0L && limit > 1) {
        Thread.sleep(delay)
      }

      Radiation(
        ts = decodeTs(dataMap.getOrElse("Date:",""),dataMap.getOrElse("Time:","")),
        area = data._1, 
        lon = decodeLon(dataMap.getOrElse("Longitude","")),
        lat = decodeLat(dataMap.getOrElse("Latitude","")),
        dose = decodeDose(dataMap.getOrElse("Ambient (Dose rate)",""))
      )      
    })
    
  }
}