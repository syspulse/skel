package io.syspulse.skel.shop.item

import scala.collection.immutable

import io.jvm.uuid._
import java.time._

final case class Item(id:UUID, ts:ZonedDateTime, name: String, count:Double, price:Double)