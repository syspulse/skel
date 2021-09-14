package io.syspulse.skel.npp.emu

import scala.util.Random
import java.time.{ZoneId,ZonedDateTime,LocalDateTime,Instant}
import java.time.format._

import io.syspulse.skel.scrap.emu._

// === Custom =========================================================================================================
case class NPP() extends cask.Routes{
  
  // Does not work if it is just extended !
  val rootUrl = "/MEDO-PS"
  @cask.get(rootUrl)
  def npp() = {
    cask.Redirect(s"${rootUrl}/index.php")
  }

  @cask.staticFiles(s"${rootUrl}/index.php",headers = Seq("Cache-Control" -> "max-age=14400"))
  def nppIndex() = {
    println(s"Telemetry request")
    s"web${rootUrl}/index.php"
  }

  // @cask.staticFiles(s"${rootUrl}/popup.php",headers = Seq("Cache-Control" -> "max-age=14400"))
  // def nppPopup() = s"web${rootUrl}/popup.php"

  @cask.get(s"${rootUrl}/popup.php")
  def nppPopup(data: Option[String]=None,location:Option[String]=None) = {
    println(s"Sensor Request: data=${data},location=${location}")
    val date = LocalDateTime.now.format(DateTimeFormatter.ofPattern("dd.MM.YYYY")) //"14.08.2021"
    val time = LocalDateTime.now.format(DateTimeFormatter.ofPattern("HH:mm")) //"14:00"
    val measure = s"${Random.nextInt(5000)} nSv"
    val lat = s"N${50.0 + 3 * Random.nextDouble()}"//"N051.391494"
    val lon = s"E${30.0 + 2 * Random.nextDouble()}"//"E030.101595"
s"""
<html>
<head>
<link rel="stylesheet" href=".//OsnovneSlike/form.css" type="text/css">
<META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8" /></head>
<BODY>
<p>
<a align="right" href="javascript:window.close()">Close</a> window.<BR>
<B>HZHTObjֵ׏7o5</B><BR><TABLE>
<TR class="even"><TD>Time:</TD><TD><B>${time}</B></TD></TD>
<TR class="odd"><TD>Date:</TD><TD><B>${date}</B></TD></TD>
<TR class="even"><TD>Ambient (Dose rate)</TD><TD><B>${measure}</B></TD></TD>
<TR class="odd"><TD>Latitude</TD><TD><B>${lat}</B></TD></TD>
<TR class="even"><TD>Longitude</TD><TD><B>${lon}</B></TD></TD>
</TABLE>
</p>
</body>
</html>
  """
  }

  initialize()
}

object NppEmu extends EmuMain(Seq(NPP())) {  
  
}
