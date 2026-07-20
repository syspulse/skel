package io.hacken.ext.detector

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import java.util.concurrent.atomic.AtomicInteger
import com.typesafe.scalalogging.Logger

import spray.json._
import io.syspulse.skel.service.JsonCommon
import java.util.concurrent.TimeUnit

import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class DetectorSchemaFaq(
  name: String,
  value: String
)

case class DetectorSchema(
  id: Int,
  createdAt: Long,
  updatedAt: Long,
  status: String, //"ACTIVE/DISABLED",
  name: String, // did == Uniqie Detector,
  version: String, //"0.2.7",
  title: String,
  description: String,
  author: String,
  icon: Option[String],
  // correct format for jsonb:
  // "[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]"
  faq: Option[Seq[DetectorSchemaFaq]],
  tags: Seq[String],
  networkTags: Seq[String],
  schema: Option[JsObject],
  uiSchema: Option[JsObject]
)

object DetectorSchemaJson extends JsonCommon {
  implicit val jf_faq_item: RootJsonFormat[DetectorSchemaFaq] = jsonFormat2(DetectorSchemaFaq)
  implicit val jf_ds: RootJsonFormat[DetectorSchema] = jsonFormat15(DetectorSchema)
}

