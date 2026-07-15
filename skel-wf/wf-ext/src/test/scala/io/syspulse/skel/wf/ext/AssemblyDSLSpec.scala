package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema}
import io.syspulse.skel.wf.ext.store.WorkflowStoreMem
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig}

class AssemblyDSLSpec extends AnyWordSpec with Matchers {
  val timeout = Duration(5, "seconds")

  "AssemblyDSL parser" should {
    "parse a bare node" in {
      AssemblyDSL.parseNode("Detector.name1") shouldBe NodeSpecLike(None, "Detector", "name1", None)
    }
    "parse a node with output link id" in {
      AssemblyDSL.parseNode("Detector.name2.0") shouldBe NodeSpecLike(None, "Detector", "name2", Some(0))
    }
    "parse a node with input and output link ids" in {
      AssemblyDSL.parseNode("1.Detector.name3.1") shouldBe NodeSpecLike(Some(1), "Detector", "name3", Some(1))
    }
    "parse a Schema-only node" in {
      val s = AssemblyDSL.parseNode("Schema.foo")
      s.isSchemaOnly shouldBe true
      s.isDetector shouldBe false
    }
    "recognise numeric id references" in {
      AssemblyDSL.parseNode("Detector.42").isById shouldBe true
      AssemblyDSL.parseNode("Detector.foo").isById shouldBe false
    }
    "split a pipeline into nodes" in {
      AssemblyDSL.parse("Detector.a -> Detector.b -> Detector.c") should have size 3
    }
    "reject a node without an entity token" in {
      intercept[IllegalArgumentException] { AssemblyDSL.parseNode("foo.bar") }
    }
  }

  // small structural matcher helper to compare NodeSpec values
  private def NodeSpecLike(in: Option[Int], entity: String, ref: String, out: Option[Int]) =
    io.syspulse.skel.wf.ext.dsl.NodeSpec(in, entity, ref, out)

  "AssemblyDSL.buildSchema (schema command)" should {
    "create a WorkflowSchema with DetectorSchemas (and NO DetectorConfigs)" in {
      val store = new WorkflowStoreMem()
      val res = Await.result(
        AssemblyDSL.buildSchema("Detector.name1 -> Detector.name2 -> Detector.name3", store, wid = Some(0), wname = Some("WAudit")),
        timeout)

      res.schema.id shouldBe 0
      res.schema.name shouldBe "WAudit"
      res.schema.graph.isTemplate shouldBe true
      res.schema.graph.nodes should have size 3
      res.schema.graph.links should have size 2

      // 3 DetectorSchema named Schema_name1/2/3, NO DetectorConfig
      res.detectorSchemas.map(_.name).toSet shouldBe Set("Schema_name1", "Schema_name2", "Schema_name3")
      res.detectorConfigs shouldBe empty
      res.config shouldBe None

      // persisted
      Await.result(store.allDetectorSchemas, timeout) should have size 3
      Await.result(store.allDetectorConfigs, timeout) shouldBe empty
      Await.result(store.getSchema(0), timeout).name shouldBe "WAudit"

      // nodes reference detector schemas by sid; cid is empty (template)
      res.schema.graph.nodes.values.foreach { n => n.cid shouldBe None }
      res.schema.graph.nodes.values.map(_.sid).toSet shouldBe res.detectorSchemas.map(_.id).toSet
    }

    "default the WorkflowSchema id to 0 when not given" in {
      val store = new WorkflowStoreMem()
      val res = Await.result(AssemblyDSL.buildSchema("Schema.a -> Schema.b", store), timeout)
      res.schema.id shouldBe 0
      res.schema.id should be >= 0
    }
  }

  "AssemblyDSL.assembly (assembly command)" should {
    "create WorkflowConfig + underlying WorkflowSchema + DetectorSchemas + DetectorConfigs" in {
      val store = new WorkflowStoreMem()
      val res = Await.result(
        AssemblyDSL.assembly("Detector.name1 -> Detector.name2.0 -> 1.Detector.name3.1", store, wid = Some(0), wname = Some("WFlow")),
        timeout)

      // 3 DetectorSchema + 3 DetectorConfig
      res.detectorSchemas.map(_.name).toSet shouldBe Set("Schema_name1", "Schema_name2", "Schema_name3")
      res.detectorConfigs.map(_.name).toSet shouldBe Set("name1", "name2", "name3")

      // WorkflowSchema (template) + WorkflowConfig (instance)
      res.schema.graph.isTemplate shouldBe true
      val cfg = res.config.getOrElse(fail("expected a WorkflowConfig"))
      cfg.sid shouldBe res.schema.id
      cfg.graph.isInstance shouldBe true
      cfg.graph.nodes should have size 3
      cfg.graph.links should have size 2

      // config nodes carry cid (DetectorConfig) references; schema nodes do not
      cfg.graph.nodes.values.flatMap(_.cid).toSet shouldBe res.detectorConfigs.map(_.id).toSet
      res.schema.graph.nodes.values.foreach { n => n.cid shouldBe None }

      // persisted
      Await.result(store.allDetectorConfigs, timeout) should have size 3
      Await.result(store.getConfig(cfg.id), timeout).id shouldBe cfg.id
      Await.result(store.getSchema(res.schema.id), timeout).id shouldBe res.schema.id
    }

    "honour explicit out/in link ids from the DSL" in {
      val store = new WorkflowStoreMem()
      val res = Await.result(
        AssemblyDSL.assembly("Detector.a.7 -> 7.Detector.b", store), timeout)
      // the single link between a(0) and b(1) should use the explicit out id 7
      val cfg = res.config.get
      cfg.graph.links.keySet shouldBe Set(7)
      val l = cfg.graph.links(7)
      l.from shouldBe 0
      l.to shouldBe 1
    }

    "reference an EXISTING DetectorConfig by id" in {
      val store = new WorkflowStoreMem()
      val now = System.currentTimeMillis()
      val ds = DetectorSchema(7, now, now, WorkflowSchema.Status.ACTIVE, "Schema_existing", WorkflowSchema.Version.DEF_VERSION, "t", "", "", None, None, Seq(), Seq(), None, None)
      val dc = DetectorConfig(5, now, now, WorkflowSchema.Status.ACTIVE,
        DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, "existing"),
        Some(DetectorConfigSchema(7, now, now, WorkflowSchema.Status.ACTIVE, "Schema_existing", WorkflowSchema.Version.DEF_VERSION, None)),
        "existing", "", Seq(), None, Seq())
      Await.result(store.addDetectorSchema(ds), timeout)
      Await.result(store.addDetectorConfig(dc), timeout)

      val res = Await.result(AssemblyDSL.assembly("Detector.newone -> Detector.5", store), timeout)
      // node referencing id 5 must not create a new DetectorConfig and must point at config 5 / schema 7
      res.detectorConfigs.map(_.name) shouldBe Seq("newone") // only the new one
      val cfg = res.config.get
      val nodeForExisting = cfg.graph.nodes(1)
      nodeForExisting.cid shouldBe Some(5)
      nodeForExisting.sid shouldBe 7
    }

    "share ONE DetectorSchema across same-named nodes but create distinct DetectorConfigs" in {
      val store = new WorkflowStoreMem()
      val res = Await.result(
        AssemblyDSL.assembly("Detector.scan -> Detector.scan -> Detector.report", store), timeout)

      // 2 schemas (Schema_scan reused, Schema_report), 3 distinct configs
      res.detectorSchemas.map(_.name) shouldBe Seq("Schema_scan", "Schema_report")
      res.detectorConfigs.map(_.name) shouldBe Seq("scan", "scan", "report")
      res.detectorConfigs.map(_.id).distinct should have size 3

      val cfg = res.config.get
      // the two `scan` nodes point at the SAME DetectorSchema (sid) but DIFFERENT DetectorConfigs (cid)
      val scanNodes = cfg.graph.nodes.values.filter(_.title == "scan").toSeq
      scanNodes should have size 2
      scanNodes.map(_.sid).distinct should have size 1
      scanNodes.flatMap(_.cid).distinct should have size 2
    }

    "fail when referencing a non-existent DetectorConfig id" in {
      val store = new WorkflowStoreMem()
      intercept[Exception] {
        Await.result(AssemblyDSL.assembly("Detector.999", store), timeout)
      }
    }
  }

  "AssemblyDSL.linkByName (link command)" should {
    // seed a store with DetectorConfigs (some with multiple versions) + their DetectorSchemas
    def seeded(): WorkflowStoreMem = {
      val store = new WorkflowStoreMem()
      val now = System.currentTimeMillis()
      def ds(id: Int, name: String, ver: String) =
        DetectorSchema(id, now, now, WorkflowSchema.Status.ACTIVE, s"Schema_${name}", ver, s"Schema_${name}", "", "", None, None, Seq(), Seq(), None, None)
      def dc(id: Int, name: String, sid: Int, ver: String) =
        DetectorConfig(id, now, now, WorkflowSchema.Status.ACTIVE,
          DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, name),
          Some(DetectorConfigSchema(sid, now, now, WorkflowSchema.Status.ACTIVE, s"Schema_${name}", ver, None)),
          name, "", Seq(), None, Seq())
      // PoO: two versions (1.0.0 and 1.2.0 -> latest); PoR: single version
      Await.result(store.addDetectorSchema(ds(10, "PoO", "1.0.0")), timeout)
      Await.result(store.addDetectorSchema(ds(11, "PoO", "1.2.0")), timeout)
      Await.result(store.addDetectorSchema(ds(20, "PoR", "1.0.0")), timeout)
      Await.result(store.addDetectorConfig(dc(100, "PoO", 10, "1.0.0")), timeout)
      Await.result(store.addDetectorConfig(dc(101, "PoO", 11, "1.2.0")), timeout)
      Await.result(store.addDetectorConfig(dc(200, "PoR", 20, "1.0.0")), timeout)
      store
    }

    "build WorkflowConfig+Schema from EXISTING DetectorConfigs (creating no Detector*)" in {
      val store = seeded()
      val res = Await.result(AssemblyDSL.linkByName("Detector.PoO -> Detector.PoR", store, wname = Some("Linked")), timeout)

      // creates nothing new
      res.detectorSchemas shouldBe empty
      res.detectorConfigs shouldBe empty
      Await.result(store.allDetectorConfigs, timeout) should have size 3
      Await.result(store.allDetectorSchemas, timeout) should have size 3

      val cfg = res.config.get
      cfg.name shouldBe "Linked"
      cfg.graph.nodes should have size 2
      cfg.graph.links should have size 1

      // PoO node points at the LATEST version (config 101 / schema 11), PoR at (200 / 20)
      val poo = cfg.graph.nodes.values.find(_.title == "PoO").get
      poo.cid shouldBe Some(101)
      poo.sid shouldBe 11
      val por = cfg.graph.nodes.values.find(_.title == "PoR").get
      por.cid shouldBe Some(200)
      por.sid shouldBe 20

      // WorkflowSchema references the DetectorSchemas of the found configs
      res.schema.graph.nodes.values.map(_.sid).toSet shouldBe Set(11, 20)
      res.schema.graph.nodes.values.foreach { n => n.cid shouldBe None }
    }

    "accept the bracket shorthand via WorkflowAssembly.linkByName" in {
      val store = seeded()
      val cfg = Await.result(
        io.syspulse.skel.wf.ext.store.WorkflowAssembly.linkByName("[PoO] -> [PoR]", store), timeout)
      cfg.graph.nodes.values.map(_.cid).flatten.toSet shouldBe Set(101, 200)
      Await.result(store.allDetectorConfigs, timeout) should have size 3 // nothing created
    }

    "fail when a DetectorConfig name is not found" in {
      val store = seeded()
      intercept[Exception] {
        Await.result(AssemblyDSL.linkByName("Detector.PoO -> Detector.DoesNotExist", store), timeout)
      }
    }
  }
}
