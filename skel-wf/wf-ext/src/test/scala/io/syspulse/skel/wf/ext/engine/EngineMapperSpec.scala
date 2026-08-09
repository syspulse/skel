package io.syspulse.skel.wf.ext.engine

import scala.concurrent.ExecutionContext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import io.hacken.ext.wf.{WorkflowGraf, WorkflowNode, WorkflowLink, WorkflowConfig, WorkflowSchema}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract}

class EngineMapperSpec extends AnyWordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  // ---- fixtures --------------------------------------------------------------
  private def dc(id: Int, name: String): DetectorConfig = {
    val now = 0L
    DetectorConfig(
      id = id, createdAt = now, updatedAt = now, status = "ACTIVE",
      contract = DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, name),
      schema = None, name = name, source = "", tags = Seq(), config = None, destinations = Seq(),
    )
  }

  /** WorkflowConfig with 3 nodes linked to DetectorConfigs 100/101/102. */
  private def config(): (WorkflowConfig, Map[Int, DetectorConfig]) = {
    val graf = WorkflowGraf(id = 0, sid = Some(0), cid = Some(0))
      .withNode(WorkflowNode(id = 0, title = "ProofOfOwnership", sid = 500, cid = Some(100)))
      .withNode(WorkflowNode(id = 1, title = "ProofOfReserve",   sid = 501, cid = Some(101)))
      .withNode(WorkflowNode(id = 2, title = "Report",           sid = 502, cid = Some(102)))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))
      .withLink(WorkflowLink(id = 1, from = 1, to = 2))
    val schema = WorkflowSchema.of(0, "PoR-Flow", graf)
    val cfg = WorkflowConfig.from(0, schema).copy(graph = graf, xid = Some("run-xyz"))
    val detectors = Map(
      100 -> dc(100, "ProofOfOwnership"),
      101 -> dc(101, "ProofOfReserve"),
      102 -> dc(102, "Report"),
    )
    (cfg, detectors)
  }

  private def runtime(): EngineWorkflow =
    EngineWorkflow(
      id = "PoR-DefaultProject-1", runtimeId = "run-xyz", name = "PoR-Flow",
      status = EngineStatus.RUNNING, namespace = "default",
      activities = Seq(
        EngineActivity("a1", "ProofOfOwnership", EngineActivity.KIND_ACTIVITY, EngineStatus.COMPLETED),
        EngineActivity("a2", "ProofOfReserve",   EngineActivity.KIND_ACTIVITY, EngineStatus.RUNNING),
      ),
    )

  "EngineMapper.map (with linked WorkflowConfig)" should {

    "map workflow status and correlate activities to DetectorConfig by name" in {
      val (cfg, detectors) = config()
      val view = EngineMapper.map(runtime(), Some(cfg), detectors)

      view.runtimeId shouldBe "run-xyz"
      view.name shouldBe "PoR-Flow"
      view.status shouldBe EngineStatus.RUNNING
      view.cid shouldBe Some(cfg.id)
      view.sid shouldBe Some(cfg.sid)
      view.steps.size shouldBe 3

      val byNode = view.steps.map(s => s.nodeId -> s).toMap
      byNode(0).name shouldBe "ProofOfOwnership"
      byNode(0).status shouldBe EngineStatus.COMPLETED
      byNode(0).matched shouldBe true
      byNode(0).cid shouldBe Some(100)

      byNode(1).status shouldBe EngineStatus.RUNNING
      byNode(1).matched shouldBe true

      // no matching activity for "Report" yet -> UNKNOWN / unmatched
      byNode(2).name shouldBe "Report"
      byNode(2).status shouldBe EngineStatus.UNKNOWN
      byNode(2).matched shouldBe false
    }

    "correlate a step to a child workflow by type name (rule 2)" in {
      val (cfg, detectors) = config()
      val w = runtime().copy(children = Seq(
        EngineWorkflow(id = "PoR-DefaultProject-1.report", runtimeId = "run-child",
          name = "Report", status = EngineStatus.COMPLETED, namespace = "default")
      ))
      val view = EngineMapper.map(w, Some(cfg), detectors)
      val report = view.steps.find(_.name == "Report").get
      report.matched shouldBe true
      report.status shouldBe EngineStatus.COMPLETED
      report.kind shouldBe Some(EngineActivity.KIND_CHILD)
      report.runtimeId shouldBe Some("run-child")
    }
  }

  "EngineMapper.map (no linked config)" should {
    "expose observed activities as steps" in {
      val view = EngineMapper.map(runtime(), None, Map())
      view.cid shouldBe None
      view.steps.map(_.name) should contain allOf ("ProofOfOwnership", "ProofOfReserve")
      view.steps.foreach(_.matched shouldBe true)
    }
  }

  "Engine.scheme" should {
    "extract the engine scheme from a --engine uri" in {
      Engine.scheme("temporal://127.0.0.1:7233/default") shouldBe "temporal"
      Engine.scheme("temporal://") shouldBe "temporal"
      Engine.isEngineUri("temporal://") shouldBe true
      Engine.isEngineUri("nope") shouldBe false
    }
  }

  "Engine.apply / panelUri" should {
    "use --engine.url as the panel base when set (HTTPS), else fall back to URI-derived UI" in {
      val withUrl = Engine("temporal://127.0.0.1:7233/default", Some("https://temporal.example.com"))
      withUrl.url shouldBe Some("https://temporal.example.com")
      withUrl.panelUri("PoR-Flow", "wid-1", "rid-1") shouldBe
        Some("https://temporal.example.com/namespaces/default/workflows/wid-1/rid-1")

      val fromUri = Engine("temporal://127.0.0.1:7233/default")
      fromUri.url shouldBe None
      fromUri.panelUri("PoR-Flow", "wid-1", "rid-1") shouldBe
        Some("http://127.0.0.1:8233/namespaces/default/workflows/wid-1/rid-1")
    }
  }
}
