package io.syspulse.skel.world.country

import io.jvm.uuid._
import io.syspulse.skel.world.country.Country

object CountryLoader {
  
  def fromResource(file:String="countries.txt"): Seq[Country] = {
    val txt = scala.io.Source.fromResource(file).getLines()
    txt.toSeq.map( s => {
      val (name,short) = s.split("\\|").toList match { case s::n => (n.head,s)}
      Country(UUID.randomUUID(), name,short)
    })
  }

}
