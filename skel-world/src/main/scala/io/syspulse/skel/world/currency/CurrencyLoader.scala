package io.syspulse.skel.world.currency

import io.jvm.uuid._
import io.syspulse.skel.util.Util
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.world.currency.Currency
import java.io.StringReader

object CurrencyLoader {
  val log = Logger(s"CurrencyLoader")

  def fromResource(file:String="currencies.csv"): Seq[Currency] = {
    log.info(s"Loading from Resource: ${file}")
    val txt = scala.io.Source.fromResource(file).getLines().toSeq.tail.mkString("\n")
    val reader = CSVReader.open(new StringReader(txt))

    val cc = reader.iterator.toSeq.map( s => {
      s match { 
        case country::name::code::numCode::_ => 
          Currency(
            Util.uuid(name,Currency.getClass().getSimpleName()),
            name, 
            code, 
            if(!numCode.isEmpty) numCode.toInt else -1, 
            country
          )
      }
    })

    log.info(s"Loaded from Resource: ${file}: ${cc.size}")
    cc
  }

}
