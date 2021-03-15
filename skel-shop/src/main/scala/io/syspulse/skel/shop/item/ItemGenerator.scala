package io.syspulse.skel.shop.item

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import com.github.javafaker._

import io.syspulse.skel.shop.item.Item
import scala.util.Random
import java.time.ZonedDateTime

object ItemGenerator {
  val log = Logger(s"${this}")
  val faker = new Faker()
  
  def random(count:Long): Seq[Item] = {
    
    val cc = for( i <- 1L to count) yield {
      Item(
        id = UUID.randomUUID(),
        ts = ZonedDateTime.now(),
        name = faker.commerce().productName(),
        count = Random.nextLong(1000).toDouble,
        price = Random.nextLong(100).toDouble
      )
    }

    log.info(s"Generated: ${cc.size}")
    cc
  }
  
}
