package io.syspulse.skel.shop.shipment

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import io.syspulse.skel.shop.shipment.Shipment

object ShipmentLoader {
  val log = Logger(s"ShipmentLoader")
  
  def from(iterator:Iterator[Seq[String]]): Seq[Shipment] = {
    
    val cc = iterator.toSeq.map( s => {
      s match { 
        case id::orderId::warehouseId::address::shipmentType::_ => 
          Shipment(
            UUID.fromString(id), 
            ZonedDateTime.now, 
            UUID.fromString(orderId),
            UUID.fromString(warehouseId),
            address,
            shipmentType
          )
      }
    })

    log.info(s"Loaded: ${cc.size}")
    cc
  }

  def fromResource(file:String="shipments.csv"): Seq[Shipment] = {
    log.info(s"Loading from Resource: ${file}")
    from(
      CSVReader.open(new StringReader(scala.io.Source.fromResource(file).getLines().mkString("\n"))).iterator
    )
  }

  def fromFile(file:String): Seq[Shipment] = {
    log.info(s"Loading from File: ${file}")
    from(
      CSVReader.open(new FileReader(file)).iterator
    )
  }
}
