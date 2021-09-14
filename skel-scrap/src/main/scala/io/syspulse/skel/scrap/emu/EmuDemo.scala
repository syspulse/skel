package io.syspulse.skel.scrap.emu

import scala.util.Random
import java.time.{ZoneId,ZonedDateTime,LocalDateTime,Instant}
import java.time.format._

import io.syspulse.skel.scrap.emu._

// === Custom =========================================================================================================
case class Demo() extends cask.Routes{
  
  // Does not work if it is just extended !
  val rootUrl = "/demo"
  @cask.get(rootUrl)
  def demo() = {
    cask.Redirect(s"${rootUrl}/index.html")
  }

  @cask.staticFiles(s"${rootUrl}/index.html",headers = Seq("Cache-Control" -> "max-age=14400"))
  def demoIndex() = {
    println(s"Demo request")
    s"web${rootUrl}/index.php"
  }
  
  initialize()
}

object EmuDemo extends EmuMain(Seq(Demo())) {  
  
}
