package io.syspulse.skel.test

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.RejectionHandler
import org.scalatest.concurrent.ScalaFutures

import org.scalatest.wordspec.{ AnyWordSpec }
import org.scalatest.matchers.should.Matchers

import io.jvm.uuid._

import akka.http.scaladsl.server.Route
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.scalatest.flatspec.AnyFlatSpec

trait HttpServiceTest extends AnyFlatSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  // Custom Rejecter which preserves JSON over 404,500, ... (TestKit replaces body and Content-Type for non-200)
  implicit def jsonPreservingRejectionHandler =
  RejectionHandler.default
    .mapRejectionResponse {
      case res @ HttpResponse(_, _, ent: HttpEntity.Strict, _) =>
        println(s"================> ${ent}")
      // since all Akka default rejection responses are Strict this will handle all rejections
        val message = ent.data.utf8String.replaceAll("\"", """\"""")
        
        println(s"================> ${message}") 
        // we copy the response in order to keep all headers and status code, wrapping the message as hand rolled JSON
        // you could the entity using your favourite marshalling library (e.g. spray json or anything else) 
        //res.copy(entity = HttpEntity(ContentTypes.`application/json`, message))
        res.withEntity(entity = HttpEntity(ContentTypes.`application/json`, message))
        
      case x => x // pass through all other types of responses
    }
  
  def getBody():String = {
    // val f  = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
    // val s = Await.result(f,Duration.Inf)
    entityAs[String]
  }
}