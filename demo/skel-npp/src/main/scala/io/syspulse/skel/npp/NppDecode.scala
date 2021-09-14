package io.syspulse.skel.npp

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
import scala.util.Random

import io.syspulse.skel.flow._
import io.syspulse.skel.geo.Geohash

class NppDecode extends Stage[NppData]("NPP-Decode") {
  
  def exec(flow:Flow[NppData]): Flow[NppData] = {
    decode(flow)
  }

  def decode(flow:Flow[NppData]): Flow[NppData] = {

    val browser = JsoupBrowser()

    val indexFile = flow.data.indexFile
    
    val radiation = flow.data.popupFiles.map{ case(area,file) => {
      val docSensor = browser.parseFile(file)
      val dataMap = (docSensor >> extractor("tr")).map(_.extract("td").map(_.text)).map( v=> v.head.trim -> v.tail.head.trim).toMap

      val lat = NppDecode.decodeLat(dataMap.getOrElse("Latitude",""))
      val lon = NppDecode.decodeLon(dataMap.getOrElse("Longitude",""))
      
      val geohash = Geohash.encode(lat,lon)
      Radiation(
        ts = NppDecode.decodeTs(dataMap.getOrElse("Date:",""),dataMap.getOrElse("Time:","")),
        area = area, 
        lon = lon,
        lat = lat,
        geohash = geohash.toString(),
        dose = NppDecode.decodeDose(dataMap.getOrElse("Ambient (Dose rate)",""))
      )      
    }}.toList
    
    flow.data.radiation = radiation
    
    flow
  }
}

object NppDecode {
  
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

}