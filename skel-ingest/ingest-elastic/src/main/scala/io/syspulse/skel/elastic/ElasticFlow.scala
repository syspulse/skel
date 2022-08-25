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

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.IngestFlow

import akka.stream.alpakka.elasticsearch.scaladsl.ElasticsearchSink
import akka.stream.alpakka.elasticsearch.WriteMessage
import akka.stream.alpakka.elasticsearch.ElasticsearchParams
import spray.json.JsonFormat

trait ElasticFlow[I,T] extends IngestFlow[I,T,WriteMessage[T,NotUsed]] with ElasticClient[T] {
  
  def getIndex(t:T):(String,T)
  
  override def sink():Sink[WriteMessage[T,NotUsed],Any] = 
    ElasticsearchSink.create[T](
      ElasticsearchParams.V7(getIndexName()), settings = getSinkSettings()
    )

  override def transform(t:T):Seq[WriteMessage[T,NotUsed]] = {
    val (index,t2) = getIndex(t)
    Seq(WriteMessage.createIndexMessage(index, t2))
  }

  //override def connect(elasticUri:String,elasticIndex:String):ElasticFlow[T] = super.connect(elasticUri,elasticIndex).asInstanceOf[ElasticFlow[T]]
}