package io.syspulse.skel.ai.agent

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

case class AiAgent(
  model:Option[String] = None,
  ts:Long = System.currentTimeMillis(),
  tags:Seq[String] = Seq(),
  meta: Map[String,Map[String,Any]] = Map()
)
