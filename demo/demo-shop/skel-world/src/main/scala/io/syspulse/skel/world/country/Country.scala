package io.syspulse.skel.world.country

import scala.collection.immutable

import io.jvm.uuid._

final case class Country(id:UUID, name: String, iso:String, native:String = "")
