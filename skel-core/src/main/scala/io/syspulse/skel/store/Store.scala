package io.syspulse.skel.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable

import io.jvm.uuid._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

// E - Entity
// P - Primary Key

trait Store[E,P] {

  def getKey(e:E):P

  def +(e:E):Future[E]
  def -(e:E)(implicit ec:ExecutionContext):Future[E] = del(getKey(e)).map(_ => e)
  def del(id:P):Future[P]
  def ?(id:P):Future[E]
  def ??(ids:Seq[P])(implicit ec:ExecutionContext):Future[Seq[E]] =
    Future.traverse(ids)(id => ?(id).map(Some(_)).recover { case _ => None }).map(_.flatten)
  def all:Future[Seq[E]]
  def size:Future[Long]
}

object Store {
  def toFuture[T](t: => Try[T]): Future[T] = Future.fromTry(t)

  def fromFuture[T]( f: => Future[T]): Try[T] = {
    try {
      Success(
        Await.result( f ,FiniteDuration(15000L,TimeUnit.MILLISECONDS))
      )
    } catch {
      case e:Exception => Failure(e)
    }
  }
}
