package io.syspulse.skel.shop.warehouse

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import io.syspulse.skel.shop.warehouse.Warehouse

object WarehouseLoader {
  val log = Logger(s"WarehouseLoader")
  
  def from(iterator:Iterator[Seq[String]]): Seq[Warehouse] = {
    
    val cc = iterator.toSeq.map( s => {
      s match { 
        case id::name::country::location::_ => Warehouse(UUID.fromString(id), ZonedDateTime.now, name, UUID.fromString(country),location)
      }
    })

    log.info(s"Loaded: ${cc.size}")
    cc
  }

  def fromResource(file:String="warehouses.csv"): Seq[Warehouse] = {
    log.info(s"Loading from Resource: ${file}")
    from(
      CSVReader.open(new StringReader(scala.io.Source.fromResource(file).getLines().mkString("\n"))).iterator
    )
  }

  def fromFile(file:String): Seq[Warehouse] = {
    log.info(s"Loading from File: ${file}")
    from(
      CSVReader.open(new FileReader(file)).iterator
    )
  }
}
