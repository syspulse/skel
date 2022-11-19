package io.syspulse.skel.flow

import scala.util.{Try,Success,Failure}

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time._
import io.syspulse.skel.util.Util

class Stage1() extends Stage[String]("Stage-1") {
  def exec(flow:Flow[String]): Flow[String] = {
    flow
  }
}

class StageError(num:Int) extends Stage[String]("Stage-1")(new RepeatErrorPolicy(delay=100L,retries=3)) {
  var count = 0
  def exec(flow:Flow[String]): Flow[String] = {
    count = count + 1
    flow.data = s"error: ${count}"
    if(count <= num) throw new Exception(s"error: ${count}")
    
    val f1 = flow.copy(data="finished")
    f1
  }
}


class FlowSpec extends AnyWordSpec with Matchers with FlowTestable {
  
  "FlowSpec" should {

    "run Pipeline with 2 errors and 3 retries as Success" in {
      val p = new Pipeline[String]("Pipeline-1",
        stages = List(
          new StageError(2)
        )
      )
      val f1 = p.run("started")
      f1.data should === ("finished")
    }

    "run Pipeline with 5 errors and 3 retries as Failure" in {
      val p = new Pipeline[String]("Pipeline-1",
        stages = List(
          new StageError(5)
        )
      )
      val f1 = p.run("started")
      f1.data should === ("error: 4")
    }
    
  }
}
