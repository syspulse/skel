package io.syspulse.skel.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import java.util.concurrent.Executors
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.dash.server.DashDataReq
import io.syspulse.skel.dash.source.DataSource
import io.syspulse.skel.dash.source.DataSourceCoingecko
import io.syspulse.skel.dash.source.DataSourceDune
import io.syspulse.skel.dash.source.DataSourceElastic
import io.syspulse.skel.dash.source.DataSourceTest
import io.syspulse.skel.db.guard.QueryGuard

object DataSourceMany {
  private val log = Logger(this.getClass)

  def resolve(uri:String,guard:QueryGuard):Seq[DataSource] = {
    val stores = uri.stripPrefix("many://").split(",").flatMap(u => {
      // val store = u.split("://").toList match {
      //   case "test" :: _ => new DataSourceTest(u)
      //   case "dune" :: _ => new DataSourceDune(u)
      //   case ("es" | "ess" ) :: _ => new DataSourceElastic(u)
      //   case ("cg" | "coingecko" ) :: _ => new DataSourceCoingecko(u)
      //   case ("sql" | "jdbc" | "postgres" ) :: _ => new DataSourceSQL(u)
      //   case _ => throw new Exception(s"unknown datasource: '${u}'")
      // }
      // store
      DataSource.resolve(u,guard) match {
        case Success(ds) => Some(ds)
        case Failure(e) => {
          log.warn(s"Failed to create datasource: '${u}': ${e.getMessage}")
          None
        }
      }
    })
    stores.toSeq
  }
}

class DataSourceMany(uri:String,guard:QueryGuard) extends DataSource {
  private val log = Logger(this.getClass)

  override def toString:String = s"DataSourceMany(${stores})"

  def src:String = "many"

  val stores = DataSourceMany.resolve(uri,guard)

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
