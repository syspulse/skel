package io.syspulse.skel.wf.ext.engine

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TrackMapperSpec extends AnyWordSpec with Matchers {

  "TrackMapper.isUuid" should {
    "recognize a Temporal RunId (UUID)" in {
      TrackMapper.isUuid("019f51c0-3917-731b-864d-3b9d326db0aa") shouldBe true
      TrackMapper.isUuid("7d807b62-7f01-4909-a966-547304798bf6") shouldBe true
    }
    "reject a WorkflowId and other non-UUID strings" in {
      TrackMapper.isUuid("PoR-DefaultProject-1783782976365") shouldBe false
      TrackMapper.isUuid("")            shouldBe false
      TrackMapper.isUuid("not-a-uuid")  shouldBe false
    }
  }

  "TrackMapper.of" should {
    "pick a RuntimeIdMapper for a UUID (fixed run)" in {
      val m = TrackMapper.of("019f51c0-3917-731b-864d-3b9d326db0aa")
      m.kind shouldBe TrackMapper.KIND_RUNTIME_ID
      m shouldBe a[RuntimeIdMapper]
      m.key shouldBe "019f51c0-3917-731b-864d-3b9d326db0aa"
    }
    "pick a WorkflowIdMapper for a WorkflowId (latest run)" in {
      val m = TrackMapper.of("PoR-DefaultProject-1783782976365")
      m.kind shouldBe TrackMapper.KIND_WORKFLOW_ID
      m shouldBe a[WorkflowIdMapper]
      m.key shouldBe "PoR-DefaultProject-1783782976365"
    }
  }
}
