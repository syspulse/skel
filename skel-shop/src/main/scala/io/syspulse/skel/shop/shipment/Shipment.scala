package io.syspulse.skel.shop.shipment

import scala.collection.immutable

import io.jvm.uuid._
import java.time._
import scala.util._

final case class Shipment(id:UUID, ts:ZonedDateTime, orderId:UUID, warehouseId:UUID, address:String, shipmentType:String)

object ShipmentType {
  def random = Vector("NOR","URG","VIP")(Random.nextInt(3))
}