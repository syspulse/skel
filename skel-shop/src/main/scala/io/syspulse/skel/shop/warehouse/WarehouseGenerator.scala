package io.syspulse.skel.shop.warehouse

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import com.github.javafaker._

import io.syspulse.skel.world.country.CountryStore
import io.syspulse.skel.shop.warehouse.Warehouse
import scala.util.Random
import java.time.ZonedDateTime

object WarehouseGenerator {
  val log = Logger(s"${this}")
  val faker = new Faker()
  val countrys = CountryStore.getDefault
  
  def random(count:Long): Seq[Warehouse] = {
    
    val cc = for( i <- 1L to count) yield {
      Warehouse(
        id = UUID.randomUUID(),
        ts = ZonedDateTime.now(),
        name = faker.commerce().productName(),
        countryId = countrys(Random.nextInt(countrys.size-1)).id,
        location = faker.address().fullAddress
      )
    }

    log.info(s"Generated: ${cc.size}")
    cc
  }
  
}
