package io.syspulse.skel.ingest

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

trait Ingestable extends Product {
    def toLog:String
    def toSimpleLog:String

    def toCSV:String = Util.toCSV(this)
}