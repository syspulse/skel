package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.util.Util
import io.syspulse.skel.notify._
import io.syspulse.skel.auth.permissions.DefaultPermissions


class NotifyStoreMem(implicit config:Config) extends NotifyBroadcast()(config) with NotifyStore {
  val log = Logger(s"${this}")

  // UserID -> Queue
  var notifys: Map[UUID,NotifyQueue] = Map()

  def all:Future[Seq[Notify]] = Future.successful(notifys.values.map( nq => nq.old ++ nq.fresh ).flatten.toSeq)

  def size:Future[Long] = Future.successful(notifys.values.map( nq => nq.old.size + nq.fresh.size ).fold(0)(_ + _))

  def notify(n:Notify):Future[Notify] = {
    for {
      _ <- `++`(n)
      _ <- Store.toFuture(broadcast(n))
    } yield n
  }

  def ++(n:Notify):Future[Notify] = {
    // parse special case for user://{uid}
    val uid:UUID = NotifyUri.isUser(n.to.getOrElse("")) match {
      case Some("user.all") =>
        // ATTENTION: Getting all Users from skel-user !!
        Util.UUID_0
      case Some(uid) =>
        UUID(uid)
      case None =>
        n.uid.orElse(Some(DefaultPermissions.USER_ADMIN)).get
    }

    log.info(s"add: ${n} --> ${uid}")

    // uids.foreach{ uid =>
    //   val nq = notifys.get(uid) match {
    //     case Some(nq) => nq.copy(fresh = nq.fresh :+ n)
    //     case None => NotifyQueue(uid,fresh = List(n))
    //   }
    //   notifys = notifys + (uid -> nq)
    // }
    val n1 = n.copy(uid = Some(uid))
    `+`(n1)

    Future.successful(n1)
  }


  def +(n:Notify):Future[Notify] = {
    // this should always resolve correctly here
    val uid = (n.uid.orElse(Some(DefaultPermissions.USER_ADMIN)).get)

    log.debug(s"add: ${n} -> ${uid}")

    val nq = notifys.get(uid) match {
      case Some(nq) =>
        if(n.ack)
          nq.copy(old = nq.old :+ n)
        else
          nq.copy(fresh = nq.fresh :+ n)
      case None =>
        if(n.ack)
          NotifyQueue(uid,old = List(n))
        else
          NotifyQueue(uid,fresh = List(n))
    }

    notifys = notifys + (uid -> nq)
    Future.successful(n)
  }

  def ?(id:UUID):Future[Notify] = notifys.values.map( nq => nq.old ++ nq.fresh ).flatten.find(_.id == id) match {
    case Some(u) => Future.successful(u)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def ??(uid:UUID,fresh:Boolean):Future[Seq[Notify]] = {
    log.debug(s"uid=${uid},fresh=${fresh}")
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

    Future.successful(nn.sortBy(- _.ts))
  }

  def find(sev:Option[NotifySeverity.ID]=None,scope:Option[String]):Future[Seq[Notify]] = {
    all.map(_.filter(n => {
      (if(sev.isDefined) n.severity == sev else true) &&
      (if(scope.isDefined) n.scope == scope else true)
    }))
  }

  def ack(id:UUID):Future[Notify] = {
    log.debug(s"ack: ${id}")

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
        Future.successful(n)
      case None =>
        Future.failed(new Exception(s"not found or already Acked: ${id}"))
    }
  }
}
