package io.syspulse.skel.twit

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Failure, Random}

import akka.actor.typed.ActorRef
import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy

import akka.stream.alpakka.cassandra.CassandraSessionSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraSession
import akka.stream.alpakka.cassandra.scaladsl.CassandraSessionRegistry
import akka.stream.alpakka.cassandra.CassandraWriteSettings
import akka.stream.alpakka.cassandra.scaladsl.CassandraFlow
import akka.stream.alpakka.cassandra.scaladsl.CassandraSource
import com.datastax.oss.driver.api.core.cql.{BoundStatement, PreparedStatement}

import com.typesafe.scalalogging.Logger
import akka.NotUsed
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.stream.scaladsl.SourceQueueWithComplete

class CassandraFlow(cassandraHosts:String, keySpace:String="twit_space",table:String="twit",queueLimit:Long = 100, scrapUsers:Seq[String] = Seq(),scrapDir:String="/tmp") {

  val log = Logger(s"${this}")

  // set cassandra hosts
  if(! cassandraHosts.isEmpty()) {
    cassandraHosts.split(",").zipWithIndex.foreach{ case (h,i) => {
      log.info(s"Cassandra Host: ${i}: ${h}")
      System.setProperty(s"datastax-java-driver.basic.contact-points.${i}",h)
    }}
  }

  implicit val system: ActorSystem = ActorSystem()
  implicit lazy val materializer: ActorMaterializer = ActorMaterializer()
  val sessionSettings = CassandraSessionSettings()
  implicit val cassandraSession: CassandraSession = CassandraSessionRegistry.get(system).sessionFor(sessionSettings)

  val statementBinder: (TwitData, PreparedStatement) => BoundStatement =
    (d, preparedStatement) => preparedStatement.bind(d.id, d.ts, d.user, d.txt)

  //val scrapStream = if(scrapUsers.size == 0) Source.queue(1,OverflowStrategy.dropNew).to(Sink.ignore).run else new scrap.ScrapFlow(scrapUsers,scrapDir).stream
  val scrapStream = if(scrapUsers.size == 0) None else Some(new scrap.ScrapFlow(scrapUsers,scrapDir).stream)

  def getUrlId(url:String) = {
    url.split("/").toIndexedSeq.last
  }

  val cassandraStream = //: Future[Seq[TwitData]] = 
    CassandraSource(s"SELECT id,ts,user,txt FROM $keySpace.$table")
      .map(row => TwitData(row.getLong("id"),row.getLong("ts"),row.getString("user"),row.getString("txt")))
      .filter(t => scrapUsers.contains(t.user))
      //.map( t => { println(s"${t}"); t} )
      .filter( t => os.exists(os.Path(s"${scrapDir}/tmp-${getUrlId(t.txt)}.jpg")))
      //.runWith(Sink.seq)
      .runWith(Sink.foreach(t => if(scrapStream.isDefined) scrapStream.get.offer(t)))
  

  val version: Future[String] = cassandraSession
      .select("SELECT release_version FROM system.local;")
      .map(_.getString("release_version"))
      .runWith(Sink.head)

  version.onComplete({
    case Success(value) => println(s"Cassandara versions: ${value}")
    case Failure(exception) => exception.printStackTrace
  })

  /*
  val tf: Future[String] = CassandraSource(s"SELECT * FROM $keyspace.$table where id=37")
    .map(_.getString("excerpt"))
    .runWith(Sink.head)
   */

}
