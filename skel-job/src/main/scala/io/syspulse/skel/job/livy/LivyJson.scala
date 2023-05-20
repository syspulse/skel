package io.syspulse.skel.job.livy

import io.syspulse.skel.job._
import io.syspulse.skel.service.JsonCommon

case class LivySessionAppInfo(driverLogUrl:Option[String],sparkUiUrl:Option[String])

case class LivySession(
  id:Long, name:Option[String],
  appId:Option[String],
  owner:Option[String],
  proxyUser:Option[String],
  state:String,
  kind:String,
  appInfo:LivySessionAppInfo,
  log:Seq[String]
)
case class LivySessions(from:Int,total:Int,sessions:Seq[LivySession])

case class LivyStatementOutput(
  status:String,
  execution_count:Int,  
  data:Option[Map[String,String]],
  ename:Option[String] = None,
  traceback:Option[Seq[String]] = None
)

case class LivyStatement(
  id:Long, 
  state:String,
  progress: Double,
  started: Long,
  completed: Long,
  code:String,  
  output:Option[LivyStatementOutput],  
)

case class LivySessionResults(total_statements:Long,statements:Seq[LivyStatement])

// "{\"kind\": \"pyspark\", \"name\":\"${NAME}\", \"conf\":{\"spark.job.param1\": 100,\"spark.job.param2\": \"UNI\"}}
case class LivySessionCreate(
  kind:String,
  name:String,
  conf:Map[String,String] = Map()
)

case class LivySessionRes(
  msg:String
)

case class LivySessionRun(
  code:String
)


import spray.json._
import DefaultJsonProtocol._

object LivyJson extends JsonCommon with NullOptions {
  implicit val jf_LSOu = jsonFormat5(LivyStatementOutput.apply _)
  implicit val jf_LSt = jsonFormat7(LivyStatement.apply _)
  implicit val jf_LSts = jsonFormat2(LivySessionResults.apply _)

  implicit val jf_LSAi = jsonFormat2(LivySessionAppInfo.apply _)
  implicit val jf_LS = jsonFormat9(LivySession)
  implicit val jf_LSsRes = jsonFormat3(LivySessions.apply _)

  implicit val jf_LSCreate = jsonFormat3(LivySessionCreate.apply _)
  implicit val jf_LSRes = jsonFormat1(LivySessionRes.apply _)

  implicit val jf_LSRun = jsonFormat1(LivySessionRun.apply _)
}
