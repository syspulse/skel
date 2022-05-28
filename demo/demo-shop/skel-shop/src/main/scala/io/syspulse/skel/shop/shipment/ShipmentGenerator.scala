package io.syspulse.skel.shop.shipment

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import com.github.javafaker._

import io.syspulse.skel.world.country.CountryStore
import io.syspulse.skel.shop.shipment.Shipment
import scala.util.Random
import java.time.ZonedDateTime

object ShipmentGenerator {
  val log = Logger(s"${this}")
  val faker = new Faker()
  val countrys = CountryStore.getDefault
  
  def random(count:Long): Seq[Shipment] = {
    
    val cc = for( i <- 1L to count) yield {
      Shipment(
        id = UUID.randomUUID(),
        ts = ZonedDateTime.now(),
        orderId = UUID.random,
        warehouseId = UUID.random,
        address = faker.address().fullAddress,
        shipmentType = ShipmentType.random
      )
    }

    log.info(s"Generated: ${cc.size}")
    cc
  }
  
}
