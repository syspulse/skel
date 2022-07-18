package io.syspulse.skel.pdf.report

import scala.collection.immutable

import io.jvm.uuid._

final case class Report(
  id:UUID, input:String = "", output:String = "", name:String = "", 
  xid:String = "CLIENT_ID", phase:String = "STARTED", template:String = "H4",ts:Long = System.currentTimeMillis()
)

final case class Reports(reports: immutable.Seq[Report])

final case class ReportCreateReq(input: String, output:String, name:String, xid: String)

final case class ReportActionRes(status: String,id:Option[UUID])

final case class ReportRes(report: Option[Report])