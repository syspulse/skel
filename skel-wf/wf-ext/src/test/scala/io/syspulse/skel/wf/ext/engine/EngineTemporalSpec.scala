package io.syspulse.skel.wf.ext.engine

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

// ============================================================================
// Engine INTEGRATION test.
//
// Read-only against a live Temporal server (a local `temporal server start-dev`
// on 127.0.0.1:7233 by default, override with env TEMPORAL_ENGINE_URI). It does
// NOT start or mutate any workflow - it only observes existing runs and asserts
// that wf-ext correctly maps their state.
//
// If the server is unreachable the tests self-CANCEL (assume) rather than FAIL,
// so the suite is safe to run in environments without a Temporal server.
// ============================================================================
class EngineTemporalSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val uri = sys.env.getOrElse("TEMPORAL_ENGINE_URI", "temporal://127.0.0.1:7233/default")
  val ns  = sys.env.getOrElse("TEMPORAL_ENGINE_NS", "default")
  val timeout = 30.seconds

  private var engine: Engine = _
  private var serverUp: Boolean = false

  override def beforeAll(): Unit = {
    engine = Engine(uri)
    serverUp = Try(Await.result(engine.namespaces(), 12.seconds)).map(_ => true).getOrElse(false)
    if (!serverUp) info(s"Temporal server not reachable at ${uri} - integration tests will be canceled")
  }

  override def afterAll(): Unit = if (engine != null) engine.close()

  private def up(): Unit = assume(serverUp, s"Temporal server not reachable at ${uri}")

  "EngineTemporal (live)" should {

    "list namespaces (excluding the internal system namespace)" in {
      up()
      val nss = Await.result(engine.namespaces(), timeout)
      nss should contain (ns)
      nss should not contain ("temporal-system")
    }

    "poll runtimes for a namespace with normalized statuses" in {
      up()
      val ws = Await.result(engine.getRuntimes(Some(ns)), timeout)
      info(s"ns=${ns}: ${ws.size} workflows: ${ws.map(w => s"${w.name}/${w.status}").distinct.mkString(", ")}")
      // every status must be a known EngineStatus and never UNKNOWN for real executions
      ws.foreach { w =>
        EngineStatus.all should contain (w.status)
        w.status should not be EngineStatus.UNKNOWN
        w.runtimeId should not be empty
        w.namespace shouldBe ns
      }
    }

    "expand a single runtime by runtimeId (activities + child workflows)" in {
      up()
      val ws = Await.result(engine.getRuntimes(Some(ns)), timeout)
      assume(ws.nonEmpty, "no workflows present in namespace")

      val target = ws.head
      val got = Await.result(engine.getRuntime(Some(ns), target.runtimeId), timeout)
      got shouldBe defined
      val w = got.get
      w.runtimeId shouldBe target.runtimeId
      w.id shouldBe target.id
      w.name shouldBe target.name
      info(s"expanded ${w.name} rid=${w.runtimeId}: ${w.activities.size} activities, ${w.children.size} children")

      // activities discovered from history must carry a name and a valid status
      w.activities.foreach { a =>
        a.name should not be empty
        EngineStatus.all should contain (a.status)
      }
      // child workflows share the parent WorkflowId prefix (requirement)
      w.children.foreach { c =>
        c.parentId shouldBe Some(w.id)
        c.id should startWith (w.id)
      }
    }

    "map a live runtime onto a WorkflowRuntimeView (no linked config)" in {
      up()
      val ws = Await.result(engine.getRuntimes(Some(ns)), timeout)
      assume(ws.nonEmpty, "no workflows present in namespace")

      val w = Await.result(engine.getRuntime(Some(ns), ws.head.runtimeId), timeout).get
      val view = EngineMapper.map(w)
      view.runtimeId shouldBe w.runtimeId
      view.status shouldBe w.status
      view.steps.size shouldBe (w.activities.size + w.flatten.drop(1).size)
    }

    "return None for an unknown runtimeId" in {
      up()
      val got = Await.result(engine.getRuntime(Some(ns), "00000000-0000-0000-0000-000000000000"), timeout)
      got shouldBe None
    }

    "resolve the latest run by WorkflowId" in {
      up()
      val ws = Await.result(engine.getRuntimes(Some(ns)), timeout)
      assume(ws.nonEmpty, "no workflows present in namespace")

      val wfId = ws.head.id
      val got = Await.result(engine.getRuntimeByWorkflowId(Some(ns), wfId), timeout)
      got shouldBe defined
      got.get.id shouldBe wfId
      got.get.runtimeId should not be empty
      EngineStatus.all should contain (got.get.status)
    }

    "resolve the same runtime via a TrackMapper (RunId vs WorkflowId)" in {
      up()
      val ws = Await.result(engine.getRuntimes(Some(ns)), timeout)
      assume(ws.nonEmpty, "no workflows present in namespace")
      val target = ws.head

      // UUID -> RuntimeIdMapper (fixed run)
      val byRun = TrackMapper.of(target.runtimeId)
      byRun.kind shouldBe TrackMapper.KIND_RUNTIME_ID
      val r1 = Await.result(byRun.resolve(engine, Some(ns)), timeout)
      r1.map(_.runtimeId) shouldBe Some(target.runtimeId)

      // WorkflowId -> WorkflowIdMapper (latest run)
      val byWf = TrackMapper.of(target.id)
      byWf.kind shouldBe TrackMapper.KIND_WORKFLOW_ID
      val r2 = Await.result(byWf.resolve(engine, Some(ns)), timeout)
      r2.map(_.id) shouldBe Some(target.id)
    }

    "poll runtimes across all namespaces" in {
      up()
      val all = Await.result(engine.getRuntimes(None), timeout)
      val one = Await.result(engine.getRuntimes(Some(ns)), timeout)
      all.size should be >= one.size
    }
  }
}
