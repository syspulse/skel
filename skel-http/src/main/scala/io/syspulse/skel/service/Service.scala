package io.syspulse.skel.service

import scala.collection.immutable

import io.jvm.uuid._

final case class Service(id:UUID, secret: String,name:String, uri:String, period:Int)