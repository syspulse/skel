package io.syspulse.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import io.syspulse.dash.server.DashData
import io.syspulse.dash.server.DashDataReq

trait DataSource {
  val DEF_LIMIT = 100

  def src:String
  def ask(req: DashDataReq, tid:Option[String] = None):Future[DashData]
}
