package io.syspulse.skel

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.Await
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import akka.stream.Materializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers.Location
import akka.util.ByteString
import akka.pattern.after

import com.typesafe.config.ConfigFactory

import io.syspulse.skel.util.Util


trait Actorable {
  // A small, self-contained runtime for async HTTP in feeds.
  // Detectors already run inside Akka, but feeds are also used in unit tests.
  // Important: build/test environment may set `-Dconfig.file=conf/application.conf`,
  // which is not present for unit tests. Use empty config to avoid FileNotFound.
  protected val as: ActorSystem = ActorSystem("ActorSystem-HttpClient", ConfigFactory.empty())
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

  def await(f: Future[String], timeout: Long = 0): String = {
    Await.result(f, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
  }
  
  private def redirectToUri(location: Uri, base: Uri): Uri = {
    // If Location is relative, resolve it against the original request URI.
    // If it is already absolute, resolvedAgainst() is a no-op.
    location.resolvedAgainst(base)
  }

  private def shouldRedirect(status: StatusCode): Boolean = status match {
    case StatusCodes.MovedPermanently |
        StatusCodes.Found |
        StatusCodes.SeeOther |
        StatusCodes.TemporaryRedirect |
        StatusCodes.PermanentRedirect => true
    case _ => false
  }

  private def nextRequestForRedirect(
    status: StatusCode,
    originalMethod: HttpMethod,
    originalBody: Option[String],
    target: Uri,
  ): (HttpMethod, Option[String], Uri) = {
    // RFC-ish behavior:
    // - 303: always switch to GET and drop body
    // - 301/302: commonly switch POST to GET (legacy browser behavior); keep others
    // - 307/308: preserve method + body
    status match {
      case StatusCodes.SeeOther =>
        (HttpMethods.GET, None, target)
      case StatusCodes.MovedPermanently | StatusCodes.Found =>
        if (originalMethod == HttpMethods.POST) (HttpMethods.GET, None, target)
        else (originalMethod, originalBody, target)
      case StatusCodes.TemporaryRedirect | StatusCodes.PermanentRedirect =>
        (originalMethod, originalBody, target)
      case _ =>
        (originalMethod, originalBody, target)
    }
  }

  private def req0(
    url: String,
    meth: HttpMethod,
    body: Option[String],
    headers0: Seq[(String, String)],
    timeout: Long,
    followRedirects: Boolean,
    redirectsLeft: Int,
  ): Future[String] = {
    // ATTENTION:
    // Whoever created this stupidity with Content-Type is a fucking retarted moron.
    // 
    val (ctHeaders, otherHeaders) =
      headers0.partition { case (k, _) => k.equalsIgnoreCase("Content-Type") }

    val contentType0: Option[ContentType] =
      ctHeaders.lastOption.flatMap { case (_, v0) =>
        val v = v0.trim

        // 1) Prefer Akka's built-in parser (covers common + many custom types)
        ContentType.parse(v).toOption.orElse {
          // 2) Fallback: parse media-type and wrap into ContentType (binary/custom types)
          MediaType.parse(v).toOption.flatMap {
            case mt: MediaType.Binary           => Some(ContentType(mt))
            case mt: MediaType.WithFixedCharset => Some(ContentType(mt))
            case _                              => None
          }
        }
      }

    val headers: Seq[HttpHeader] =
      otherHeaders.flatMap { case (k, v) =>
        HttpHeader.parse(k, v) match {
          case HttpHeader.ParsingResult.Ok(h, _) => Some(h)
          case _                                 => Some(RawHeader(k, v)) // best-effort fallback
        }
      }

    val entity: RequestEntity =
      body match {
        case Some(b) => HttpEntity(contentType0.getOrElse(ContentTypes.NoContentType), ByteString(b))
        case None    => HttpEntity.Empty
      }

    val req = HttpRequest(
      uri = Uri(url), 
      method = meth, 
      headers = headers,      
      entity = entity,
    )

    val f = Http()(as)
      .singleRequest(req)
      .flatMap { res =>
        val isRedirect = followRedirects && redirectsLeft > 0 && shouldRedirect(res.status)
        val locationOpt = res.header[Location].map(_.uri)

        if (isRedirect && locationOpt.isDefined) {
          val base = req.uri
          val target = redirectToUri(locationOpt.get, base)
          val (meth2, body2, uri2) = nextRequestForRedirect(res.status, meth, body, target)

          // Ensure connection can be released back to pool.
          res.discardEntityBytes()(mat)

          req0(
            url = uri2.toString(),
            meth = meth2,
            body = body2,
            headers0 = headers0,
            timeout = timeout,
            followRedirects = followRedirects,
            redirectsLeft = redirectsLeft - 1,
          )
        } else {
        val bodyF: Future[String] =
          if (timeout <= 0) {
            res.entity.dataBytes
              .runFold(ByteString.empty)(_ ++ _)(mat)
              .map(_.utf8String)(ec)
          } else {
            res.entity
              // enforces a timeout on consuming the response entity (and materializes it), 
              // which helps ensure the response body is fully read (or fails fast) so the connection can be released back to the pool.
              .toStrict(timeout.millis)
              .map(_.data.utf8String)(ec)
          }

        bodyF.flatMap { body =>
          if (res.status.isSuccess()) 
            Future.successful(body)
          else 
            Future.failed(new Exception(s"HTTP request failed: ${res.status.intValue()}: ${req.method.value} ${url}: body=${body.take(512)}"))
        }(ec)
        }
      }(ec)

    if(timeout <= 0) f else withTimeout(f, timeout)
  }

  def req(
    url: String,
    meth: HttpMethod,
    body: Option[String] = None,
    headers0: Seq[(String, String)] = Seq.empty,
    timeout: Long = 0,
    followRedirects: Boolean = true,
    maxRedirects: Int = 5,
  ): Future[String] = {    
    req0(
      url = url,
      meth = meth,
      body = body,
      headers0 = headers0,
      timeout = timeout,
      followRedirects = followRedirects,
      redirectsLeft = if (maxRedirects < 0) 0 else maxRedirects,
    )
  }

  def get(url: String, body: Option[String] = None, headers: Seq[(String, String)] = Seq.empty, timeout: Long = 0): Future[String] = {
    req(url, HttpMethods.GET, body, headers, timeout)
  }

  def post(url: String, body: Option[String] = None, headers: Seq[(String, String)] = Seq.empty, timeout: Long = 0): Future[String] = {
    req(url, HttpMethods.POST, body, headers, timeout)
  }

  def put(url: String, body: Option[String] = None, headers: Seq[(String, String)] = Seq.empty, timeout: Long = 0): Future[String] = {
    req(url, HttpMethods.PUT, body, headers, timeout)
  }

  def delete(url: String, body: Option[String] = None, headers: Seq[(String, String)] = Seq.empty, timeout: Long = 0): Future[String] = {
    req(url, HttpMethods.DELETE, body, headers, timeout)
  }
    
}

