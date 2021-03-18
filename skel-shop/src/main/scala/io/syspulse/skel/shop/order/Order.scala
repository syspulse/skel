package io.syspulse.skel.shop.order

import scala.collection.immutable

import io.jvm.uuid._
import java.time._
import scala.util._

final case class Order(id:UUID, ts:ZonedDateTime, item:UUID, orderStatus:String)

object OrderStatus {
  val statuses = Vector("0","Ready","Paid","Ship","Complete")
  def random = statuses(Random.nextInt(statuses.size))
}