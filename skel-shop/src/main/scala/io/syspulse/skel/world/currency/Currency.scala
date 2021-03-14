package io.syspulse.skel.world.currency

import scala.collection.immutable

import io.jvm.uuid._

final case class Currency(id:UUID, name: String, code:String, numCode:Int, country:String)