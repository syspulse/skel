package io.syspulse.ai.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.ai.Ai

final case class Ais(data: Seq[Ai],total:Option[Long]=None)

final case class AiCreateReq(
    question:String,
    oid:Option[String],
    model:Option[String] = None
)

final case class AiRes(status:String,Ai: Option[Ai])
