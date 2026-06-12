package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import spray.json._
import io.hacken.ext.wf._
import io.hacken.ext.wf.WorkflowGrafJson._

class WorkflowGrafSpec extends AnyWordSpec with Matchers {

  def node(id: Int, sid: Int, x: Int = 0, y: Int = 0, w: Int = 100, h: Int = 100): WorkflowNode =
    WorkflowNode(id = id, name = s"node-${id}", sid = sid,
      meta = Some(Map("pos_x" -> x, "pos_y" -> y, "size_width" -> w, "size_height" -> h, "style" -> "node.style1")))

  def link(id: Int, from: Int, to: Int): WorkflowLink = WorkflowLink(id = id, from = from, to = to)

  "WorkflowGraf topology" should {

    "be a template when cid is None and an instance when cid is Some" in {
      WorkflowGraf(id = 0).isTemplate shouldBe true
      WorkflowGraf(id = 0).isInstance shouldBe false
      WorkflowGraf(id = 0, cid = Some(5)).isInstance shouldBe true
      WorkflowGraf(id = 0, cid = Some(5)).isTemplate shouldBe false
    }

    "hold an empty graph" in {
      val g = WorkflowGraf(id = 0)
      g.nodes shouldBe empty
      g.links shouldBe empty
    }

    "keep node positions and sizes in meta" in {
      val n = node(1, 10, x = 50, y = 60, w = 200, h = 120)
      n.meta.get("pos_x") shouldBe 50
      n.meta.get("pos_y") shouldBe 60
      n.meta.get("size_width") shouldBe 200
      n.meta.get("size_height") shouldBe 120
    }

    "link two nodes and sync the link onto BOTH endpoints" in {
      val g = WorkflowGraf(id = 0)
        .withNode(node(0, 100)).withNode(node(1, 101))
        .withLink(link(0, 0, 1))

      g.links should have size 1
      g.nodes(0).links.keySet shouldBe Set(0) // outgoing link attached to source
      g.nodes(1).links.keySet shouldBe Set(0) // and to target
    }

    "support many-to-many connections to the same node" in {
      // 0 -> 2, 1 -> 2, 2 -> 3 : node 2 has 3 links attached (two in, one out)
      val g = WorkflowGraf(id = 0)
        .withNode(node(0, 10)).withNode(node(1, 11)).withNode(node(2, 12)).withNode(node(3, 13))
        .withLink(link(0, 0, 2)).withLink(link(1, 1, 2)).withLink(link(2, 2, 3))

      g.links should have size 3
      g.nodes(2).links.keySet shouldBe Set(0, 1, 2)
      g.prev(2).map(_.id).toSet shouldBe Set(0, 1)
      g.next(2).map(_.id).toSet shouldBe Set(3)
    }

    "navigate next/prev across a linear pipeline" in {
      val g = WorkflowGraf(id = 0)
        .withNode(node(0, 10)).withNode(node(1, 11)).withNode(node(2, 12))
        .withLink(link(0, 0, 1)).withLink(link(1, 1, 2))

      g.next(0).map(_.id) shouldBe Seq(1)
      g.next(1).map(_.id) shouldBe Seq(2)
      g.next(2) shouldBe empty
      g.prev(0) shouldBe empty
      g.prev(2).map(_.id) shouldBe Seq(1)
    }

    "WorkflowGraf.sync rebuilds node.links from graf.links" in {
      val raw = WorkflowGraf(
        id = 0,
        nodes = Map(0 -> node(0, 10), 1 -> node(1, 11)),
        links = Map(0 -> link(0, 0, 1)),
      )
      raw.nodes(0).links shouldBe empty // not synced yet
      val g = WorkflowGraf.sync(raw)
      g.nodes(0).links.keySet shouldBe Set(0)
      g.nodes(1).links.keySet shouldBe Set(0)
    }
  }

  "WorkflowGraf JSON" should {

    "round-trip a graph with Int-keyed node/link maps and meta" in {
      val g = WorkflowGraf(id = 7, sid = Some(1), cid = Some(2))
        .withNode(node(0, 10, x = 5, y = 6, w = 80, h = 40)).withNode(node(1, 11))
        .withLink(link(0, 0, 1))

      val json = g.toJson
      // Int keys are serialized as String object keys
      json.asJsObject.fields("nodes").asJsObject.fields.keySet shouldBe Set("0", "1")
      json.asJsObject.fields("links").asJsObject.fields.keySet shouldBe Set("0")

      val g2 = json.convertTo[WorkflowGraf]
      g2.id shouldBe 7
      g2.sid shouldBe Some(1)
      g2.cid shouldBe Some(2)
      g2.nodes.keySet shouldBe Set(0, 1)
      g2.links.keySet shouldBe Set(0)
      g2.nodes(0).meta.get("pos_x") shouldBe 5
      g2.nodes(0).meta.get("size_width") shouldBe 80
      g2.nodes(0).links.keySet shouldBe Set(0)
    }

    "round-trip an empty graph" in {
      val g = WorkflowGraf(id = 0)
      g.toJson.convertTo[WorkflowGraf] shouldBe g
    }
  }
}
