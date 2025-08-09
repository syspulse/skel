package io.syspulse.skel.ai.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.ai.Ai

final case class Ais(data: Seq[Ai],total:Option[Long]=None)

final case class AiCreateReq(
    question:String,
    instructions:Option[String] = None,
    oid:Option[String],
    model:Option[String] = None,
    id:Option[String] = None // thread_id / responses_id
)

final case class AiRes(status:String,Ai: Option[Ai])
