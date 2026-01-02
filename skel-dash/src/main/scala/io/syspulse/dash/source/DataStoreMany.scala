package io.syspulse.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import java.util.concurrent.Executors
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import io.syspulse.dash.server.DashData
import io.syspulse.dash.server.DashDataReq
import io.syspulse.dash.source.DataSource
import io.syspulse.dash.source.DataSourceCoingecko
import io.syspulse.dash.source.DataSourceDune
import io.syspulse.dash.source.DataSourceElastic
import io.syspulse.dash.source.DataSourceTest

class DataSourceMany(uri:String) extends DataSource {
  private val log = Logger(this.getClass)

  override def toString:String = s"DataSourceMany(${stores})"

  def src:String = "many"

  val stores = uri.stripPrefix("many://").split(",").map(u => {
    val store = u.split("://").toList match {
      case "test" :: _ => new DataSourceTest(u)
      case "dune" :: _ => new DataSourceDune(u)
      case ("es" | "ess" ) :: _ => new DataSourceElastic(u)
      case ("cg" | "coingecko" ) :: _ => new DataSourceCoingecko(u)
      case _ => throw new Exception(s"unknown datasource: '${u}'")
    }
    store
  }).toList

  def ask(req:DashDataReq, tid:Option[String] = None):Future[DashData] = {
    stores
      .find(_.src == req.src)
      .map(store => store.ask(req, tid))
      .getOrElse({
        log.warn(s"${req.id}: unknown datasource: '${req.src}'")
        Future.failed(new Exception(s"unknown datasource: '${req.src}'"))
      })
    
  }
  
}
