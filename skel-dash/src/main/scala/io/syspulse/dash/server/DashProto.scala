package io.syspulse.dash.server

import scala.collection.immutable
import io.jvm.uuid._

import spray.json.JsValue
import io.syspulse.skel.util.Util
import spray.json._

import io.syspulse.dash.Dash
import spray.json.JsObject


final case class DashData(
  id:String,  // Data Source id
  src:String, // data source
  fmt:String, // format of data
  data:JsObject, // data from datasource in Datasource format

  ts0:Long,
  ts:Long 
) 

final case class DashDataReq(
  id:String,  // Data Source id
  src:String, // data source
  fmt:Option[String] = None, // format of data, default is derived by Dash
  query:Option[JsValue] = None,  
  
  typ:Option[String] = None, // reserved for data type
  opts:Option[Map[String,String]] = None, // options for data source

  limit:Option[Int] = Some(100)
) 

// ===================================================================================
// WARNING:
// this is super inefficent but ok for protoype
object DashLayout {
  def fromDash(dash:Dash):DashLayout = DashLayout(
    id = dash.id,
    layout = JsonParser(dash.layout).asJsObject,
    name = dash.name,
    desc = dash.info,
    tags = dash.tags,
    pid = dash.pid,
    tid = dash.tid,
    ts = dash.ts,
    ts0 = dash.ts0
  )
}

final case class DashLayout(
  id:String,  // dashid
  
  layout:JsObject, // this is json layout
  
  name:Option[String] = None,
  desc:Option[String] = None,
  tags:Option[Vector[String]] = None,  

  pid:Option[String], // this is project id for Chat
  tid:Option[String], // tenantId (group)

  ts:Long = System.currentTimeMillis(),
  ts0:Long = System.currentTimeMillis()  
) {

  def toDash:Dash = Dash(
    id = id,
    layout = layout.toString(),
    name = name,
    info = desc,
    tags = tags,
    pid = pid,
    tid = tid
  )

}

// ===================================================================================

final case class Dashs(
  data:Seq[DashLayout],
  total:Option[Long] = None
)

final case class DashCreateReq(
  layout:JsObject,

  name:Option[String] = None,  
  desc:Option[String] = None,
  tags:Option[Vector[String]] = None,

  pid:Option[String], // owner
  tid:Option[String], // owner
)

final case class DashUpdateReq(
  id:Option[String] = None,
  layout:Option[JsObject],

  name:Option[String] = None,
  desc:Option[String] = None, 
  tags:Option[Vector[String]] = None,

  pid:Option[String], // project id
  tid:Option[String], // tenantId (group)
)

final case class DashRes(
  id: String  
)