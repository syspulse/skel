package io.syspulse.skel.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.dash.server.DashDataReq

trait DataSource {
  val DEF_LIMIT = 100

  def src:String
  def ask(req: DashDataReq, tid:Option[String] = None):Future[DashData]
}

object DataSource {
  def resolve(uri:String):Try[DataSource] = {
    uri.split("://").toList match {
      case "test" :: _ => Try(new DataSourceTest(uri))
      case "dune" :: _ => Try(new DataSourceDune(uri))
      case ("es" | "ess" ) :: _ => Try(new DataSourceElastic(uri))
      case ("cg" | "coingecko" ) :: _ => Try(new DataSourceCoingecko(uri))
      case ("sql" | "jdbc" | "postgres" ) :: _ => Try(new DataSourceSQL(uri))
      case "many" :: _ => Try(new DataSourceMany(uri))
      case _ => Failure(new Exception(s"unknown datasource: '${uri}'"))
    }
  }
}
