package io.syspulse.skel.lake.job.server

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.lake.job.Job

final case class Jobs(jobs: immutable.Seq[Job],total:Option[Long])

final case class JobCreateReq(name:String,src:String,conf:Seq[String]=Seq(),inputs:Seq[String] = Seq())

final case class JobRes(status: String,id:Option[Job.ID])
