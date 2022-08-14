package io.syspulse.skel.ingest.dynamo

import scala.jdk.CollectionConverters._

import scala.util.Random
import software.amazon.awssdk.services.dynamodb.model.AttributeValue

import io.syspulse.skel.video._

object VideoDynamo {
  
  def toDynamo(o:Video) = Map(
    "VID" -> AttributeValue.builder.s(o.vid.toString).build(),
    "TS" -> AttributeValue.builder.n(o.ts.toString).build(),
    "TITLE" -> AttributeValue.builder.s(o.title).build(),
  )

  def fromDynamo(m:Map[String,AttributeValue]) = Video(
    vid = VID(m.get("VID").map(_.s()).getOrElse("")),
    ts = m.get("TS").map(_.n()).getOrElse("0").toLong,
    title = m.get("TITLE").map(_.s()).getOrElse(""),
  )
}