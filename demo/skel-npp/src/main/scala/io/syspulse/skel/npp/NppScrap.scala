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

import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

import os._

class NppScrap(rootUrl:String = "http://localhost:30004/MEDO-PS", limit:Int = Int.MaxValue, delay:Long = 1000L, delayVariance:Long = 1L) 
    extends Stage[NppData]("NPP-Scrap")(new RepeatErrorPolicy(delay=60000L)) {
  
  def sensorUrl(href:String) = s"${rootUrl}/${href}"

  def exec(flow:Flow[NppData]): Flow[NppData] = {
    scrap(flow)
  }

  def getUrl(s:String) = s.stripPrefix("javascript:popup('")

  def scrap(flow:Flow[NppData]): Flow[NppData] = {

    val browser = JsoupBrowser()

    log.info(s"Scraping -> NPP(${rootUrl})")

    val doc = browser.get(s"${rootUrl}/index.php?online=1")

    val indexFile = s"${flow.location}/index.php"

    flow.data.indexFile = indexFile
    // overwrite file
    os.write.over(os.Path(indexFile), doc.toString)

    if(delay > 0L && limit > 1) {
      Thread.sleep(delay + Random.nextLong(delayVariance))
    }

    val sensorData = (doc >> extractor("area")).map(e => (e.attr("alt"),getUrl(e.attr("href"))))

    sensorData.take(limit).map( data =>{
      log.info(s"Scraping -> NPP(${data})")
      val docSensor = browser.get(sensorUrl(data._2))

      val sensorFile = s"${flow.location}/${data._1}-${Util.sha256(data._2)}"
      
      flow.data.popupFiles.addOne(data._1 -> sensorFile)
      os.write.over(os.Path(sensorFile), docSensor.toString)

      if(delay > 0L && limit > 1) {
        Thread.sleep(delay + Random.nextLong(delayVariance))
      }      
    })
    
    flow
  }
}