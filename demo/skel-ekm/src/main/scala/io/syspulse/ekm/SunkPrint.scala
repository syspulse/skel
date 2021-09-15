package io.syspulse.ekm

import akka.Done
import akka.actor._

import akka.http.scaladsl._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaRanges,MediaTypes, HttpMethods }

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.scaladsl.{ Sink, Source, Flow, FileIO, Tcp}
import akka.util.ByteString

import scala.concurrent.duration._
import java.nio.file.Paths

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util


class SunkPrint(config:Config) extends EkmTelemetryClient(config) {

  def run():Future[_] = {
    val flow = Flow[EkmTelemetry].map(e => {println(s"${e}"); e;})
    val stream = ekmSourceRestartable.alsoTo(logSink).via(flow).runWith(Sink.ignore)
    stream
  }

}