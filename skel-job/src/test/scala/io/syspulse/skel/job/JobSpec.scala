package io.syspulse.skel.job

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.syspulse.skel.lake.job._
import io.syspulse.skel.service.JsonCommon

import spray.json._
import DefaultJsonProtocol._
import io.syspulse.skel.job.livy._

class JobSpec extends AnyWordSpec with Matchers {
  import LivyJson._

  "LivyEngine" should {

    "return all Livy sessions" in {
      val engine = JobUri("livy://http://emr.hacken.cloud:8998")
      info(s"${engine}")
      
      //val r = engine.all()
      //info(s"${r}")      
    }

//     "decode Livy Sessions response" in {
//       val res = """{
//         "from":0,"total":1,
//         "sessions":[{
//           "id":139,"name":null,"appId":"application_1666278265701_0150",
//           "owner":null,"proxyUser":null,"state":"busy","kind":"pyspark",
//           "appInfo":{
//             "driverLogUrl":"http://ip-10-0-2-251.eu-west-1.compute.internal:8042/node/containerlogs/container_1666278265701_0150_01_000001/livy",
//             "sparkUiUrl":"http://ip-10-0-2-119.eu-west-1.compute.internal:20888/proxy/application_1666278265701_0150/"
//           },
//           "log":[
//               "\t ApplicationMaster RPC port: -1",
//               "\t queue: default",
//               "\t start time: 1679837184049",
//               "\t final status: UNDEFINED",
//               "\t tracking URL: http://ip-10-0-2-119.eu-west-1.compute.internal:20888/proxy/application_1666278265701_0150/",
//               "\t user: livy",
//               "23/03/26 13:26:24 INFO ShutdownHookManager: Shutdown hook called",
//               "23/03/26 13:26:24 INFO ShutdownHookManager: Deleting directory /mnt/tmp/spark-fc4bd7ba-c6a7-44c2-be3e-5fcc19856701",
//               "23/03/26 13:26:24 INFO ShutdownHookManager: Deleting directory /mnt/tmp/spark-ce67bead-816d-4bd3-bd5b-23ad1ffad259",
//               "\nYARN Diagnostics: "
//           ]
//         }]
//       }"""

//       val json = res.parseJson.convertTo[LivySessions]
//       json.sessions(0).id should === (139)
      
//     }

//     "decode Session Results response" in {
//       val res = """
// {
//   "total_statements": 18,
//   "statements": [
//     {
//       "id": 0,
//       "code": "spark",
//       "state": "available",
//       "output": {
//         "status": "ok",
//         "execution_count": 0,
//         "data": {
//           "text/plain": "<pyspark.sql.session.SparkSession object at 0x7f96ecfd6cd0>"
//         }
//       },
//       "progress": 1,
//       "started": 1679837207414,
//       "completed": 1679837207416
//     },
//     {
//       "id": 1,
//       "code": "from pyspark.sql import SparkSession\nfrom datetime import *\nfrom dateutil.relativedelta import *\nfrom decimal import Decimal\nspark = (\n        SparkSession\n        .builder\n        .config('spark.jars.packages', 'org.apache.hadoop:hadoop-aws:3.2.2')\n        .config('spark.hadoop.fs.s3a.impl', 'org.apache.hadoop.fs.s3a.S3AFileSystem')\n    .getOrCreate()\n    )\n\nzero = Decimal(0)\nschema = \"timestamp long, block_number long, token_address string, sender_address string, receiver_address string, value decimal(38, 0), transaction_hash string, log_index int\"\nholders_schema = \"address string, quantity decimal(38, 0)\"\n",
//       "state": "available",
//       "output": {
//         "status": "ok",
//         "execution_count": 1,
//         "data": {
//           "text/plain": ""
//         }
//       },
//       "progress": 1,
//       "started": 1679837207995,
//       "completed": 1679837208897
//     }
//   ]
// }"""
//       val json = res.parseJson.convertTo[LivySessionResults]
      
//       info(s"${json}")
//       json.statements(0).id should === (0)
//       json.statements(0).output.status should === ("ok")
//       json.statements(1).id should === (1)
//       json.statements(1).output.status should === ("ok")
//     }

//     "get Livy session by xid" in {
//       val engine = JobUri("livy://http://emr.hacken.cloud:8998")
//       val r = engine.ask("139")
//       // info(s"${r}")
//     }


    // "create Livy session and delete" in {
    //   val engine = JobUri("livy://http://emr.hacken.cloud:8998")
            
    //   val r1 = engine.create("App-1",Map("param" -> "0"))
    //   val xid = r1.get.xid
      
    //   xid should !== ("")
    //   // info(s"xid = ${xid}: ${r1.get}")

    //   val r2 = engine.del(xid)
    //   r2.get should === ("deleted")
    // }


    // "create Livy session, run script, delete" in {
    //   val engine = JobUri("livy://http://emr.hacken.cloud:8998")
            
    //   val r1 = engine.create("App-2",Map("param" -> "10"))
    //   val xid = r1.get.xid
      
    //   xid should !== ("")
    //   info(s"xid = ${xid}: ${r1.get}")

    //   val script = """print("v=",10)"""
    //   var r2 = engine.run(r1.get,script)

    //   val r3 = engine.del(xid)
    //   r3.get should === ("deleted")
    // }

    // state: "starting" -> "idle" -> 
    "run script, wait for results, delete" in {
      val engine = JobUri("livy://http://emr.hacken.cloud:8998")
            
      val r1 = engine.create("App-3",Map("spark.job.param1" -> "100","spark.job.param2" -> "Text"))
      val xid = r1.get.xid
      
      xid should !== ("")
      info(s"xid = ${xid}: ${r1.get}")

      Thread.sleep(1000)

      val script = """print(19)"""

      var r2 = engine.run(r1.get,script)
      info(s"r2 = ${r2}")
      Thread.sleep(1000)

      val r3 = engine.ask(r2.get)
      info(s"r3 = ${r3}")
      
      Thread.sleep(1000)
      val r4 = engine.ask(r3.get)
      info(s"r4 = ${r4}")

      engine.del(r4.get)
      //r3.get should === ("runnging")
    }

  }
}
