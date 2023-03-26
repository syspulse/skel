package io.syspulse.skel.lake.job.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.lake.job._

// object JobRegistry {
//   val log = Logger(s"${this}")
  
//   final case class CreateJob(jobCreate: JobReq, replyTo: ActorRef[Job]) extends Command
  
//   final case class DeleteJob(id: UUID, replyTo: ActorRef[JobActionRes]) extends Command
  
//   // this var reference is unfortunately needed for Metrics access
//   var store: JobStore = null

//   def apply(store: JobStore): Behavior[io.syspulse.skel.Command] = {
//     this.store = store
//     registry(store)
//   }

//   private def registry(store: JobStore): Behavior[io.syspulse.skel.Command] = {
//     this.store = store

//     Behaviors.receiveMessage {

//       case CreateJob(jobReq, replyTo) =>
//         log.info(s"${jobReq}")
//         val job = Job(
//           jobReq.to,
//           jobReq.subj, 
//           jobReq.msg, 
//           System.currentTimeMillis(),
//           severity = jobReq.severity,
//           scope = jobReq.scope
//         )
        
//         val store1 = store.+(job)

//         replyTo ! job
//         registry(store1.getOrElse(store))
//     }
//   }
// }
