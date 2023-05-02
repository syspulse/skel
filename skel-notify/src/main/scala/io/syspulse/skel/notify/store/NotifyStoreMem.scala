package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util
import io.syspulse.skel.notify._


class NotifyStoreMem(implicit config:Config) extends NotifyBroadcast()(config) with NotifyStore {
  val log = Logger(s"${this}")
    
  // UserID -> Queue
  var notifys: Map[UUID,NotifyQueue] = Map()

  def all:Seq[Notify] = notifys.values.map( nq => nq.old ++ nq.fresh ).flatten.toSeq

  def size:Long = notifys.values.map( nq => nq.old.size + nq.fresh.size ).fold(0)(_ + _)

  def +(n:Notify):Try[NotifyStore] = { 
    val uid = n.uid.orElse(Some(Util.UUID_0)).get
    log.info(s"add: ${n} -> ${uid}")

    val nq = notifys.get(uid) match {
      case Some(nq) => nq.copy(fresh = nq.fresh :+ n)
      case None => NotifyQueue(uid,fresh = List(n))
    }
    notifys = notifys + (uid -> nq)
    
    broadcast(n).map(_ => this)    
  }

  def ?(id:UUID):Try[Notify] = all.find(_.id == id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def ??(uid:UUID,fresh:Boolean):Seq[Notify] = {
    log.info(s"uid=${uid},fresh=${fresh}")
    val nn = notifys.get(uid) match {
      case Some(nq) => 
        if(fresh) 
          nq.fresh
        else
          nq.old ++ nq.fresh
      case None => 
        log.error(s"not found: ${uid}")
        Seq()
    }

    nn.sortBy(- _.ts)
  }

  def find(sev:Option[NotifySeverity.ID]=None,scope:Option[String]):Seq[Notify] = {
    all.filter(n => {
      (if(sev.isDefined) n.severity == sev else true) &&
      (if(scope.isDefined) n.scope == scope else true)
    })
  }
  
  def ack(id:UUID):Try[Notify] = {
    log.info(s"ack: ${id}")
    
    val nqn = notifys.values.flatMap(nq =>{
      val f = nq.fresh.find(_.id == id)
      //val o = nq.old.find(_.id == id)
      if(f.isDefined) Some((nq,f.get)) else
      //if(o.isDefined) Some((nq,o.get)) else
        None
    }).headOption
    
    nqn match {
      case Some((nq,n)) =>
        n.ack = true
        nq.fresh = nq.fresh.filter(_.id != n.id)
        nq.old = nq.old :+ n
        Success(n)
      case None => 
        Failure(new Exception(s"not found or already Acked: ${id}"))
    }
  }
}
