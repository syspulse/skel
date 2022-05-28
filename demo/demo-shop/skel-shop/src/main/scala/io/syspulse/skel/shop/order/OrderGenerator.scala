package io.syspulse.skel.shop.order

import io.jvm.uuid._
import java.time._
import com.github.tototoshi.csv._
import com.typesafe.scalalogging.Logger

import java.io.StringReader
import java.io.FileReader

import com.github.javafaker._

import io.syspulse.skel.world.country.CountryStore
import io.syspulse.skel.shop.order.Order
import scala.util.Random
import java.time.ZonedDateTime

object OrderGenerator {
  val log = Logger(s"${this}")
  val faker = new Faker()
  val countrys = CountryStore.getDefault
  
  def random(count:Long): Seq[Order] = {
    
    val cc = for( i <- 1L to count) yield {
      Order(
        id = UUID.randomUUID(),
        ts = ZonedDateTime.now(),
        //items = (0 to Random.nextInt(5)).map(_=>UUID.random),
        item = UUID.random,
        
        orderStatus = OrderStatus.random
      )
    }

    log.info(s"Generated: ${cc.size}")
    cc
  }
  
}
