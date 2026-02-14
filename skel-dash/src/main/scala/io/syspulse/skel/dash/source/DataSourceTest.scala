package io.syspulse.skel.dash.source

import scala.util.{Failure,Success,Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.collection.immutable
import java.util.concurrent.Executors
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID

import scala.concurrent.Promise
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem

import spray.json.JsObject
import spray.json.JsString
import spray.json.JsonParser

import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.dash.source.TestURI
import io.syspulse.skel.dash.server.DashDataReq
import io.syspulse.skel.dash.source.DataSource
import io.syspulse.skel.db.guard.QueryGuard

class DataSourceTest(uri:String,guard:QueryGuard) extends DataSource {
  private val log = Logger(this.getClass)

  def src:String = "test"  

  val test = TestURI(uri)
  val (delay,threads,async) = (test.delay,test.threads,test.async)

  implicit val sys: ActorSystem = ActorSystem("DataSourceTest")
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))

  val data = if(test.rsp.isDefined) 
     os.read(os.Path(test.rsp.get,os.pwd))
  else 
"""{"execution_id":"01JVM1P59STT56BBEBRPW3RGY8","query_id":4438599,"is_execution_finished":true,"state":"QUERY_STATE_COMPLETED","submitted_at":"2025-05-19T10:33:31.193422Z","expires_at":"2025-08-17T10:35:05.844947Z","execution_started_at":"2025-05-19T10:33:33.434839Z","execution_ended_at":"2025-05-19T10:35:05.844946Z","result":{"rows":[{"avalanche_tvl":857540.210912,"eth_tvl":18272668.016938,"manta_tvl":"2080561.775","metis_tvl":6435167.186845999,"total_tvl":27645937.189696}],"metadata":{"column_names":["eth_tvl","total_tvl","metis_tvl","manta_tvl","avalanche_tvl"],"column_types":["double","double","double","decimal(10, 3)","double"],"row_count":1,"result_set_bytes":139,"total_row_count":1,"total_result_set_bytes":48,"datapoint_count":5,"pending_time_millis":2241,"execution_time_millis":92410}}}"""

  def ask(req:DashDataReq, tid:Option[String] = None):Future[DashData] = {
    guard.isAllowed(req.query.getOrElse("").toString(), Map("lang" -> "sql")) match {
      case Success(true) => // Continue processing
      case Success(false) => 
        return Future.failed(new Exception("Query Rejected"))
      case Failure(e) => 
        return Future.failed(new Exception(s"Query validation failed: ${e.getMessage}"))
    }

    if (async)
      askAsync(req.id,req.limit,tid) 
    else 
      askSync(req.id,req.limit,tid)
  }

  def askSync(id:String,limit:Option[Int],tid:Option[String] = None):Future[DashData] = {
    val ts0 = System.currentTimeMillis()
    
    //Future.successful(
    Future {
      //log.info(s"test($id,$limit): ${delay}...")
      Thread.sleep(delay)
      val res = DashData(
        id,
        src,
        "test",
        JsonParser(data).asJsObject,
        ts0 = ts0,
        ts = System.currentTimeMillis()
      )
      log.info(s"test_sync($id,$limit): ->")
      res
    }
  }

  def askAsync(id:String,limit:Option[Int],tid:Option[String] = None):Future[DashData] = {
    val ts0 = System.currentTimeMillis()
    
    val promise = Promise[DashData]()
    
    sys.scheduler.scheduleOnce(
      FiniteDuration(delay, TimeUnit.MILLISECONDS)
    ) {
      val res = DashData(
        id,
        src,
        "test",
        JsonParser(data).asJsObject,
        ts0 = ts0,
        ts = System.currentTimeMillis()
      )
      log.info(s"test_async($id,$limit): ->")
      promise.success(res)
    }
    
    promise.future
  }
}
