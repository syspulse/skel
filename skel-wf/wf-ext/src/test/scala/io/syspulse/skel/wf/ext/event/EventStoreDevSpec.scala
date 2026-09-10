package io.syspulse.skel.wf.ext.event

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Try

import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/**
 * Dev OpenSearch — query only. Must never CREATE or DELETE.
 * Uses the existing `detector-alert-search` alias (yearly `detector-alert-YYYY` indices).
 */
class EventStoreDevSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val ec: ExecutionContext = ExecutionContext.global
  val timeout = 25.seconds

  lazy val uriOpt: Option[String] = ElasticEnv.devSearchUri()
  lazy val storeOpt: Option[EventStoreElastic] = uriOpt.map(u => EventStoreElastic(u))

  def store: EventStoreElastic = storeOpt.get

  def reachable: Boolean = storeOpt.exists { s =>
    Try(Await.result(s.query(EventQuery(from = Some(0), size = Some(1))), timeout)).isSuccess
  }

  override def afterAll(): Unit = {
    storeOpt.foreach(s => Try(s.close()))
    super.afterAll()
  }

  "EventStoreElastic (dev, query-only)" should {

    "search detector-alert-search and return Alert-shaped hits" in {
      assume(uriOpt.isDefined, "env.dev not present")
      assume(reachable, "Dev OpenSearch not reachable")
      val page = Await.result(store.query(EventQuery(from = Some(0), size = Some(3))), timeout)
      page.total should be > 0L
      page.alerts should not be empty
      val a = page.alerts.head
      a.id should include(":")
      a.eid should not be empty
      a.deid should be > 0L
      a.teid should be >= 0L
      a.id shouldBe Alert.elasticId(a.deid, a.eid)
    }

    "get by Elastic key (_id = did:eid)" in {
      assume(uriOpt.isDefined)
      assume(reachable)
      val sample = Await.result(store.query(EventQuery(from = Some(0), size = Some(1))), timeout).alerts.head
      val got = Await.result(store.getById(sample.id), timeout)
      got.isDefined shouldBe true
      got.get.eid shouldBe sample.eid
      got.get.deid shouldBe sample.deid
    }

    "get by Alert eid" in {
      assume(uriOpt.isDefined)
      assume(reachable)
      val sample = Await.result(store.query(EventQuery(from = Some(0), size = Some(1))), timeout).alerts.head
      val found = Await.result(store.getByEid(sample.eid), timeout)
      found.map(_.eid).distinct shouldBe Seq(sample.eid)
    }

    "query by oid (teid) / did and time range without writing" in {
      assume(uriOpt.isDefined)
      assume(reachable)
      val sample = Await.result(store.query(EventQuery(from = Some(0), size = Some(1))), timeout).alerts.head
      val byOid = Await.result(store.query(EventQuery(oid = Some(sample.teid), from = Some(0), size = Some(5))), timeout)
      byOid.alerts.foreach(_.teid shouldBe sample.teid)

      val byDid = Await.result(store.query(EventQuery(did = Some(sample.deid), from = Some(0), size = Some(5))), timeout)
      byDid.alerts.foreach(_.deid shouldBe sample.deid)

      val ts0 = sample.ts - 1
      val ts1 = sample.ts + 1
      val byTs = Await.result(store.query(EventQuery(ts0 = Some(ts0), ts1 = Some(ts1), from = Some(0), size = Some(5))), timeout)
      byTs.alerts.foreach { a =>
        a.ts should be >= ts0
        a.ts should be <= ts1
      }
    }
  }
}
