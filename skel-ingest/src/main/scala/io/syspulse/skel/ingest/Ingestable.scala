package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

abstract class Ingestable {
    def toLog:String
    def toSimpleLog:String
}