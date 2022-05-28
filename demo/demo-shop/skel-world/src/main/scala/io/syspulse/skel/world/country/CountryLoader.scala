package io.syspulse.skel.world.country

import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.world.country.Country
import com.typesafe.scalalogging.Logger

object CountryLoader {
  val log = Logger(s"CountryLoader")
  
  def fromResource(file:String="countries.csv"): Seq[Country] = {
    log.info(s"Loading from Resource: ${file}")

    val txt = scala.io.Source.fromResource(file).getLines()
    val cc = txt.toSeq.map( s => {
      val (name,iso) = s.split("\\|").toList match { case s::n => (n.head,s)}
      Country(
        Util.uuid(name,Country.getClass().getSimpleName()), 
        name, 
        iso, 
        ""
      )
    })

    log.info(s"Loaded from Resource: ${file}: ${cc.size}")
    cc
  }

}
