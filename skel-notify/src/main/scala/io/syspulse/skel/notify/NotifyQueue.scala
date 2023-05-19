package io.syspulse.skel.notify

import scala.collection
import scala.collection.immutable

import io.jvm.uuid._

final case class NotifyQueue(
  uid:UUID,
  var old:List[Notify] = List(),
  var fresh:List[Notify] = List()
)
