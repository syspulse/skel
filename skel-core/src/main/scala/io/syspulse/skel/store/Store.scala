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

  def +(e:E):Try[E]
  def -(e:E):Try[E] = del(getKey(e)).map(_ => e)
  def del(id:P):Try[P]
  def ?(id:P):Try[E]
  def ??(ids:Seq[P]):Seq[E] = ids.flatMap(id => ?(id).toOption)
  def all:Seq[E]
  def size:Long

  // Experiment to support the same Store
  def +!(e:E):Future[E] = throw new NotImplementedError()
  def -!(e:E)(implicit ec:ExecutionContext):Future[E] = delAsync(getKey(e)).map(_ => e)
  def delAsync(id:P):Future[P] = throw new NotImplementedError()
  def ?!(id:P):Future[E] = throw new NotImplementedError()
  def ??!(ids:Seq[P])(implicit ec:ExecutionContext):Future[Seq[E]] = Future.sequence(ids.map(id => ?!(id)))
  def allAsync:Future[Seq[E]] = throw new NotImplementedError()
  def sizeAsync:Future[Long] = throw new NotImplementedError()
}

object Store {
  def fromFuture[T]( f: => Future[T]) = {
    try {      
      Success(
        Await.result( f ,FiniteDuration(15000L,TimeUnit.MILLISECONDS))
      )
    } catch {
      case e:Exception => Failure(e)
    }
  }
}
