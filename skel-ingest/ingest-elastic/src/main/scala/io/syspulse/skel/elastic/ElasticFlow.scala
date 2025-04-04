package io.syspulse.skel.elastic

import scala.jdk.CollectionConverters._

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}
import akka.util.ByteString

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import scala.concurrent.ExecutionContext.Implicits.global 
import scala.util.Random
import java.nio.file.{Paths,Files}
import scala.jdk.CollectionConverters._

import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.ElasticsearchParams

import io.syspulse.skel.ingest.{IngestFlow,IngestFlowPipeline}
import io.syspulse.skel.util.Util

trait ElasticFlow[I,T] extends IngestFlowPipeline[I,T,WriteMessage[T,NotUsed]] with ElasticClient[T] {
  
  def getIndex(t:T):(String,T)
  
  override def sink():Sink[WriteMessage[T,NotUsed],Any] = 
    ElasticsearchSink.create[T](
      ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
    )

  override def transform(t:T):Seq[WriteMessage[T,NotUsed]] = {
    val (index,t2) = getIndex(t)
    Seq(WriteMessage.createIndexMessage(index, t2))
  }

  def shaping:Flow[T,T,_] = Flow[T].map(t => t)

}