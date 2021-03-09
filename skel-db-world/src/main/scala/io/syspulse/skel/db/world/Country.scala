package io.syspulse.skel.db.world

import scala.collection.immutable

import io.jvm.uuid._

final case class Country(id:UUID, name: String, short:String)