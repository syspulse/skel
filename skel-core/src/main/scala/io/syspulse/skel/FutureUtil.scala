package io.syspulse.skel

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

//import spray.json.DefaultJsonProtocol._
import com.typesafe.scalalogging.Logger

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.actor.ActorSystem
import akka.pattern.after
import com.typesafe.config.ConfigFactory

import akka.NotUsed

import scala.concurrent.Future
import scala.util.{ Failure, Success }

import io.jvm.uuid._

class FutureAwaitable[T](f:Future[T],timeout:Duration = FutureUtil.timeout0)  {
  def await[R]() = Await.result(f,timeout)
}

object FutureUtil {
  val timeout0 = FiniteDuration(5,TimeUnit.SECONDS)

  private lazy val timeoutAs = ActorSystem("FutureUtil-timeout", ConfigFactory.empty())

  def withTimeout[A](f: Future[A], timeoutMs: Long)(implicit ec: ExecutionContext): Future[A] = {
    if (timeoutMs <= 0) return f
    val timeoutF = after(FiniteDuration(timeoutMs, TimeUnit.MILLISECONDS), timeoutAs.scheduler) {
      Future.failed(new java.util.concurrent.TimeoutException(s"timeout: ${timeoutMs} ms"))
    }(ec)
    Future.firstCompletedOf(Seq(f, timeoutF))(ec)
  }

  implicit def ftor[R](f: Future[R]):FutureAwaitable[R] = new FutureAwaitable[R](f)
  
  implicit def await[R](f: Future[R])(implicit timeout:Duration = Duration.Inf):R = {
    Await.result(f,timeout)
  }

  implicit def ????[R](f: Future[R])(implicit timeout:Long = 5000):R = {
    if(timeout <= 0) return Await.result(f,Duration.Inf)
    Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS))
  }

  implicit def sync[R](f: Future[R])(implicit timeout:Long = 5000):Try[R] = {
    Try(Await.result(f,FiniteDuration(timeout,TimeUnit.MILLISECONDS)))
  }
}


trait AwaitableService[T <: AwaitableService[T]] {
  var timeout:FiniteDuration = FutureUtil.timeout0
  
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
