package io.syspulse.skel.shop.item

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import io.syspulse.skel.shop.item.Item

object ItemLoader {
  val log = Logger(s"ItemLoader")
  
  def from(iterator:Iterator[Seq[String]]): Seq[Item] = {
    
    val cc = iterator.toSeq.map( s => {
      s match { 
        case country::name::count::cost::_ => Item(UUID.randomUUID(), ZonedDateTime.now, name, count.toDouble, cost.toDouble)
      }
    })

    log.info(s"Loaded: ${cc.size}")
    cc
  }

  def fromResource(file:String="items.csv"): Seq[Item] = {
    log.info(s"Loading from Resource: ${file}")
    from(
      CSVReader.open(new StringReader(scala.io.Source.fromResource(file).getLines().mkString("\n"))).iterator
    )
  }

  def fromFile(file:String): Seq[Item] = {
    log.info(s"Loading from File: ${file}")
    from(
      CSVReader.open(new FileReader(file)).iterator
    )
  }
}
