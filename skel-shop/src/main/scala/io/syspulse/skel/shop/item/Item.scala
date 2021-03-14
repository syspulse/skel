package io.syspulse.skel.shop.item

import scala.collection.immutable

import io.jvm.uuid._

final case class Item(id:UUID, name: String, count:Double, price:Double)