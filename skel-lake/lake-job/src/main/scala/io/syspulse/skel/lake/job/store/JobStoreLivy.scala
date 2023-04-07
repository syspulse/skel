package io.syspulse.skel.lake.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.lake.job._
import io.syspulse.skel.lake.job.Job.ID
import io.syspulse.skel.lake.job.livy.LivyHttp

// class JobStoreLivy(engine:JobEngine,uri:String)(implicit config:Config) extends JobStore {
//   val log = Logger(s"${this}")

//   //val engine = JobUri(uri) //new LivyHttp(config.jobEngine)(config.timeout)

//   def getEngine = engine

//   def +(name:String,script:String,data:List[String]):Try[Job] = {
//     val job = JobEngine.pipeline(engine,name,script,data)
//     job
//   }
  
//   def del(id:ID):Try[JobStore] = {
//     engine.del(Job(xid=id)).map(_ => this)
//   }

//   def ?(id:ID):Try[Job] = {
//     engine.ask(Job(xid=id))
//   }

//   def all:Seq[Job] = {
//     engine.all() match {
//       case Success(aa) => aa
//       case Failure(e) => 
//         log.error(s"could not get jobs",e)
//         Seq()
//     }
//   }

//   def size:Long = all.size

// }
