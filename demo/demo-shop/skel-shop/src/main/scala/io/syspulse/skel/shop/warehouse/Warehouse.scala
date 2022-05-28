package io.syspulse.skel.shop.warehouse

import scala.collection.immutable

import io.jvm.uuid._
import java.time._

final case class Warehouse(id:UUID, ts:ZonedDateTime, name: String, countryId:UUID, location:String)