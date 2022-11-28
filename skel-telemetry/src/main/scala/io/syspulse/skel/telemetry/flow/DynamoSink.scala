package io.syspulse.skel.ingest.dynamo

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

import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient
import akka.stream.alpakka.dynamodb.scaladsl.DynamoDb
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest
import scala.util.Random
import software.amazon.awssdk.services.dynamodb.model.AttributeValue
import java.nio.file.{Paths,Files}

import scala.jdk.CollectionConverters._

import io.syspulse.skel.util.Util
import io.syspulse.skel.Ingestable
import io.syspulse.skel.ingest.uri.DynamoURI
import io.syspulse.skel.telemetry.store.DynamoClient
import scala.util.Success
import scala.util.Failure

trait DynamoFormat[T] {
  
  def toDynamo(o:T):Map[String,AttributeValue]    
  def fromDynamo(m:Map[String,AttributeValue]):T
}

object FlowsDynamo {
  
  def toDynamo[T <: Ingestable](uri:String)(fmt:DynamoFormat[T]) = {
    val dynamo = new ToDynamo[T](DynamoURI(uri),fmt)
    // Flow[T]
    // //  .mapConcat(t => dynamo.transform(t))
    //   .toMat(dynamo.sink())(Keep.right)
    dynamo.sink()
  }
}

class ToDynamo[T <: Ingestable](dynamoUri:DynamoURI,fmt:DynamoFormat[T]) extends DynamoClient(dynamoUri) with DynamoSink[T] {
  val sink0 = sink(dynamoUri)(dynamoClient,fmt)
  def sink():Sink[T,_] = sink0
  //def transform(t:T):Seq[T] = Seq(t)
}


trait DynamoSink[T] {
  
  def sink(dynamoUri:DynamoURI)(implicit client:DynamoDbAsyncClient,fmt:DynamoFormat[T]) = {
    Flow[T]
      //.viaMat(KillSwitches.single)(Keep.right)
      .map(t => {
        
        val req = PutItemRequest
          .builder()
          .tableName(dynamoUri.table)
          .item(fmt.toDynamo(t).asJava)
          .build()

        //log.debug(s"${t}: ${req}")
        req
      })
      .via(DynamoDb.flow(parallelism = 1))
      // .map( r => {
      //   //log.debug(s"res: ${r}")
      //   r
      // })
      .toMat(
        //Sink.foreach(println)
        Sink.ignore
      )(Keep.both)
  }
}