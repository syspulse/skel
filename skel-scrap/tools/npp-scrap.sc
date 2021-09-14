import $ivy.`net.ruippeixotog::scala-scraper:2.2.1`
import net.ruippeixotog.scalascraper.browser.JsoupBrowser

import net.ruippeixotog.scalascraper.dsl.DSL._
import net.ruippeixotog.scalascraper.dsl.DSL.Extract._
import net.ruippeixotog.scalascraper.dsl.DSL.Parse._
import net.ruippeixotog.scalascraper.model._

val browser = JsoupBrowser()
val doc = browser.parseFile("index.php.html")

// (doc >> extractor("area")).map(e => (e.attr("coords"),e.attr("alt")))
// def ~~(s:String) = s.stripPrefix("\'").stripSuffix("\'")
// def ~~!(s:String) = s.stripPrefix("\'").stripSuffix("\');")
// def parse(href:String) = href.split(",") match { case Array(_,_,station,v1,v2,v3,v4) => (~~(v1),~~(v2),~~(v3),~~!(v4)) }

def getUrl(s:String) = s.stripPrefix("javascript:popup('")
val sensorHrefs = (doc >> extractor("area")).map(e => (e.attr("alt"),getUrl(e.attr("href"))))

val rootUrl = "http://www.srp.ecocentre.kiev.ua/MEDO-PS"
def sensorUrl(href:String) = s"${rootUrl}/${href}"

val docSensor = browser.parseFile("sensor-1.html")

(docSensor >> extractor("tr")).map(_.extract("td").map(_.text)).flatMap( _ match { 
  case List("Time:",v) => Some(v); 
  case _ => None 
})

val dataMap = (docSensor >> extractor("tr")).map(_.extract("td").map(_.text)).map( v=> v.head.trim -> v.tail.head.trim).toMap