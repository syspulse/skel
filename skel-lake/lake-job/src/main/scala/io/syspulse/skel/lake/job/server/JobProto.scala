package io.syspulse.skel.lake.job.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.lake.job.Job

final case class Jobs(jobs: immutable.Seq[Job],total:Option[Long])

final case class JobSubmitReq(name:String,src:String,conf:Option[Map[String,String]]=None,inputs:Option[Map[String,Any]] = None)

final case class JobRes(status: String,id:Option[Job.ID])
