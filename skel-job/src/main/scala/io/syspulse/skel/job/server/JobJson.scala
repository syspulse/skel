package io.syspulse.skel.job.server

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.job.Job
import io.syspulse.skel.job.server._

object JobJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Job = jsonFormat13(Job.apply _)
  implicit val jf_Jobs = jsonFormat2(Jobs)
  implicit val jf_JobRes = jsonFormat2(JobRes)
  implicit val jf_SubmitReq = jsonFormat4(JobSubmitReq)
}
