package io.syspulse.skel

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

//import spray.json.DefaultJsonProtocol._
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.NotUsed

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import io.jvm.uuid._

class FutureAwaitable[T](f:Future[T],timeout:Duration = FutureAwaitable.defaultAwaitabletimeout)  {
  def await[R]() = Await.result(f,timeout)
}

object FutureAwaitable {
  val defaultAwaitabletimeout = FiniteDuration(5,TimeUnit.SECONDS)
  implicit def ftor[R](f: Future[R]):FutureAwaitable[R] = new FutureAwaitable[R](f)
  
  implicit def await[R](f: Future[R])(implicit timeout:Duration = Duration.Inf):R = {
    Await.result(f,timeout)
  }

  implicit def awaitTimeout[R](f: Future[R])(implicit timeout:Long):R = {
    Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS))
  }

  implicit def awaitTry[R](f: Future[R])(implicit timeout:Long = 5000):Try[R] = {
    Try(Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS)))
  }
}


trait AwaitableService[T <: AwaitableService[T]] {
  var timeout:FiniteDuration = FutureAwaitable.defaultAwaitabletimeout
  
  def await[R](rsp:Future[R]):R = {
    val r = Await.result(rsp,timeout)
    r
  }

  def withTimeout(timeout:FiniteDuration = FiniteDuration(1000, MILLISECONDS)):T = {
    this.timeout = timeout
    // a bit dirty
    this.asInstanceOf[T]
  }

}
