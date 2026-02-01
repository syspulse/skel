package io.syspulse.skel.dash.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import io.syspulse.skel.util.Util
import io.syspulse.skel.Command

import io.syspulse.skel.dash._
import io.syspulse.skel.dash.server._
import io.syspulse.skel.dash.source.DataSource


object DashRegistry {
  val log = Logger(s"${this}")
        
  final case class AskDash(id:String,tid:Option[String],pid:Option[String], replyTo: ActorRef[Try[DashLayout]]) extends Command
  final case class AskDashs(tid:Option[String],pid:Option[String], replyTo: ActorRef[Try[Dashs]]) extends Command
  final case class CreateDash(tid:Option[String],pid:Option[String], req:DashCreateReq, replyTo: ActorRef[Try[DashRes]]) extends Command
  final case class UpdateDash(id:String,tid:Option[String],pid:Option[String], req:DashUpdateReq, replyTo: ActorRef[Try[DashRes]]) extends Command
  final case class DeleteDash(id:String,tid:Option[String],pid:Option[String], replyTo: ActorRef[Try[DashRes]]) extends Command

  final case class AskData(id:String,tid:Option[String],pid:Option[String],req:DashDataReq, replyTo: ActorRef[Try[DashData]]) extends Command

  def apply(store: DashStore,ds:DataSource)(implicit config:Config): Behavior[io.syspulse.skel.Command] = {
    registry(store,ds)(config)
  }
  
  private def registry(store: DashStore,ds:DataSource)(config:Config): Behavior[io.syspulse.skel.Command] = {    
    // implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    // Create custom ExecutionContext  
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threads))

    Behaviors.receiveMessage {      
      
      case AskDash(id, tid, pid, replyTo) =>
        val r = store
          .???(id,tid,pid)
          .filter(d => !tid.isDefined || d.tid == tid)
          .map(d => DashLayout.fromDash(d))
        replyTo ! r
        Behaviors.same

      case AskDashs(tid, pid, replyTo) =>
        val r = store
           .all(tid,pid)
           .filter(d => !tid.isDefined || d.tid == tid)
           
        replyTo ! Success(Dashs(r.map(d => DashLayout.fromDash(d)),total = Some(r.size)))
        Behaviors.same

      case UpdateDash(id,tid,pid,req, replyTo) =>
        log.info(s"UpdateDash($id,$tid,$pid),${req.name},${req.desc},${req.tags}")

        val r = store
          .???(id,tid,pid)
          .filter(d => !tid.isDefined || d.tid == tid)
          .map(d => d.copy(
            ts = System.currentTimeMillis(),
            layout = if(req.layout.isDefined) req.layout.get.toString() else d.layout,
            name = if(req.name.isDefined) req.name else d.name,
            info = if(req.desc.isDefined) req.desc else d.info,
            tags = if(req.tags.isDefined) req.tags else d.tags            
          ))
          .flatMap(d => store.+(d))

        r match {
          case Success(d) =>
            replyTo ! Success(DashRes(d.id))
          case Failure(e)=> 
            log.error(s"failed to update dash: ${tid}/${pid}/${id}",e)
            replyTo ! Failure(e)
        }        
        Behaviors.same

      case CreateDash(tid,pid,req, replyTo) =>
        log.info(s"CreateDash($tid,$pid),${req.name}")
        val r = store.+(
          Dash(
            id = UUID.randomUUID().toString,
            layout = req.layout.toString(),
            name = req.name,
            info = req.desc,
            tags = req.tags,
            pid = pid,  
            tid = tid
          )
        )

        r match {
          case Success(d) =>
            replyTo ! Success(DashRes(d.id))
          case Failure(e)=> 
            log.error(s"failed to create dash: ${tid}/${pid}",e)
            replyTo ! Failure(e)
        }        
        Behaviors.same
      
      case DeleteDash(id,tid,pid, replyTo) =>
        val r = store.del(id,tid,pid)

        r match {
          case Success(d) =>
            replyTo ! Success(DashRes(id))
          case Failure(e)=> 
            log.error(s"failed to delete dash: ${tid}/${pid}/${id}",e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      
      case AskData(id,tid,pid,req, replyTo) =>
        // check there is a dash and dash belongs to us        
        val d = store
          .???(id,tid,pid)
          .filter(d => !tid.isDefined || d.tid == tid)
        
        d match {
          case Success(d) =>
            // log.info(s"AskData($id,$tid,$pid,${req.limit}): ==>")
            ds.ask(req,tid)
              .map(d => {
                log.debug(s"${tid}/${pid}/${id}: ${d}")
                Success(d)
              })
              .recover { case e => {
                log.warn(s"${tid}/${pid}/${id}: ${e.getMessage}")
                Failure(e) 
              }}
              .foreach(replyTo ! _)

          case Failure(e) =>
            log.warn(s"${tid}/${pid}/${id}: ${e.getMessage}")
            replyTo ! Failure(e)
        }
                        
        Behaviors.same
    }
  }

}
