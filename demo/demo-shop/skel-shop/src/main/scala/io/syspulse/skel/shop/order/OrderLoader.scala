package io.syspulse.skel.shop.order

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import io.syspulse.skel.shop.order.Order

object OrderLoader {
  val log = Logger(s"OrderLoader")
  
  def from(iterator:Iterator[Seq[String]]): Seq[Order] = {
    
    val cc = iterator.toSeq.map( s => {
      s match { 
        case id::item::orderStatus::_ => 
          Order(
            UUID.fromString(id), 
            ZonedDateTime.now, 
            //items.split("|").map(iid=>UUID.fromString(iid)).toSeq,
            UUID.fromString(item),
            orderStatus
          )
      }
    })

    log.info(s"Loaded: ${cc.size}")
    cc
  }

  def fromResource(file:String="orders.csv"): Seq[Order] = {
    log.info(s"Loading from Resource: ${file}")
    from(
      CSVReader.open(new StringReader(scala.io.Source.fromResource(file).getLines().mkString("\n"))).iterator
    )
  }

  def fromFile(file:String): Seq[Order] = {
    log.info(s"Loading from File: ${file}")
    from(
      CSVReader.open(new FileReader(file)).iterator
    )
  }
}
