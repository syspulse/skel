package io.syspulse.skel.ingest.dynamo

import akka.actor.ActorSystem
import akka.stream.scaladsl.Keep
import akka.{Done, NotUsed}

import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Await, ExecutionContext, Future}

import akka.stream.ActorMaterializer
import akka.stream._
import akka.stream.scaladsl._

import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext.Implicits.global

import com.github.matsluni.akkahttpspi.AkkaHttpClient
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider,DefaultCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import akka.http.scaladsl.settings.ConnectionPoolSettings
import java.net.URI


trait DynamoClient {
  val log = Logger(s"${this}")

  implicit val system = ActorSystem("ActorSystem-DynamoClient")

  private val credentialsProvider = DefaultCredentialsProvider.create //StaticCredentialsProvider.create(AwsBasicCredentials.create("x", "x"))
  
  private def getDynamoClient(dynamoUri:String): DynamoDbAsyncClient = {
    val client = DynamoDbAsyncClient
    .builder()
    .region(Region.AWS_GLOBAL)
    .credentialsProvider(credentialsProvider)
    .httpClient(
      AkkaHttpClient.builder()
        .withActorSystem(system)
        .build()
    )
    .endpointOverride(new URI(dynamoUri))
    // Possibility to configure the retry policy
    // see https://doc.akka.io/docs/alpakka/current/aws-shared-configuration.html
    // .overrideConfiguration(...)
    .build()

    system.registerOnTermination(client.close())
    client
  }

  // cannot use Option because of implicit
  implicit var dynamo: DynamoDbAsyncClient = null
  var tableName:String = ""

  def getTable():String = tableName

  def connect(dynamoUri:String,dynamoTable:String):DynamoClient = {
    dynamo = getDynamoClient(dynamoUri)
    tableName = dynamoTable
    log.info(s"Dynamo: ${dynamoUri}: table=${dynamoTable}")
    this
  }

}