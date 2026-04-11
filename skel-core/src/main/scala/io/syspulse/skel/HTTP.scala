package io.syspulse.skel

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import akka.stream.Materializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import akka.pattern.after

import com.typesafe.config.ConfigFactory

import io.syspulse.skel.util.Util

trait Actorable {
  // A small, self-contained runtime for async HTTP in feeds.
  // Detectors already run inside Akka, but feeds are also used in unit tests.
  // Important: build/test environment may set `-Dconfig.file=conf/application.conf`,
  // which is not present for unit tests. Use empty config to avoid FileNotFound.
  protected val as: ActorSystem = ActorSystem("ActorSystem-FeedHttp", ConfigFactory.empty())
  protected val ec: ExecutionContext = as.dispatcher
  protected implicit val mat: Materializer = SystemMaterializer(as).materializer
  
   def withTimeout[A](f: Future[A], timeout: Long): Future[A] = {
    if (timeout <= 0) return f
    
    val timeoutF = after(FiniteDuration(timeout, TimeUnit.MILLISECONDS), as.scheduler) {
      Future.failed(new java.util.concurrent.TimeoutException(s"timeout: ${timeout} ms"))
    }(ec)

    Future.firstCompletedOf(Seq(f, timeoutF))(ec)
  }
}

object HTTP extends Actorable {
  
  def get(url: String, timeout: Long = 0, headers: Seq[(String, String)] = Seq.empty): Future[String] = {
    val req = HttpRequest(uri = Uri(url), method = HttpMethods.GET, headers = headers.map(h => RawHeader(h._1, h._2)))

    val f = Http()(as)
      .singleRequest(req)
      .flatMap { res =>
        val bodyF: Future[String] =
          if (timeout <= 0) {
            res.entity.dataBytes
              .runFold(ByteString.empty)(_ ++ _)(mat)
              .map(_.utf8String)(ec)
          } else {
            res.entity
              .toStrict(timeout.millis)
              .map(_.data.utf8String)(ec)
          }

        bodyF.flatMap { body =>
          if (res.status.isSuccess()) 
            Future.successful(body)
          else 
            Future.failed(new Exception(s"HTTP call failed: ${res.status.intValue()}: ${url}: body=${body.take(512)}"))
        }(ec)
      }(ec)

    withTimeout(f, timeout)
  }  
  
}

