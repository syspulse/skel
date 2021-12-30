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

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.video._

trait DynamoSink extends DynamoClient {
  
  def run(file:String = "/dev/stdin"):DynamoSink = {
  
    val stdinSource: Source[ByteString, Future[IOResult]] = StreamConverters.fromInputStream(() => System.in)
    // this is non-streaming simple ingester for TmsParser. Reads full file, flattens it and parses into Stream of Tms objects
    val fileSource: Source[ByteString, Future[IOResult]] = FileIO.fromPath(Paths.get(file),chunkSize = Files.size(Paths.get(file)).toInt)
    
    val source = fileSource.mapConcat(txt => TmsParser.fromString(txt.utf8String).toSeq)

    val result =
      source
      .viaMat(KillSwitches.single)(Keep.right)
      .map(tms => {
        
        val req = PutItemRequest
          .builder()
          .tableName(getTable())
          .item(MovieDynamo.toDynamo(Movie(vid=tms.id, title=tms.title)).asJava)
          .build()

        println(s"${Util.now}} ${tms}: ${req}")
        req
      })
      .via(DynamoDb.flow(parallelism = 1))
      .run()

    val r = Await.result(result, Duration.Inf)
    println(s"Result: ${r}")
    this
  }

  override def connect(dynamoUri:String,dynamoTable:String):DynamoSink = super.connect(dynamoUri,dynamoTable).asInstanceOf[DynamoSink]

}