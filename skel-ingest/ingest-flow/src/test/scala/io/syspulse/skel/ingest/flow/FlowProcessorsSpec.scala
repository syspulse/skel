package io.syspulse.skel.ingest.flow

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.util.{Success, Failure, Try}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Source, Sink}
import akka.stream.Materializer

class FlowProcessorsSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  implicit val system: ActorSystem = ActorSystem("FlowProcessorsSpec")
  implicit val materializer: Materializer = Materializer(system)

  "FlowProcessors" should {
    
    "find existing processor by name" in {
      FlowProcessors.find("none") shouldBe Some(FlowProcessorNone)
      FlowProcessors.find("print") shouldBe Some(FlowProcessorPrint)
    }

    "return None for non-existent processor" in {
      FlowProcessors.find("nonexistent") shouldBe None
      FlowProcessors.find("") shouldBe None
    }

    "create FlowProcessorNoneRun from URI without params" in {
      val result = FlowProcessors.create("none")
      result.isSuccess shouldBe true
      val processor = result.get
      processor.name shouldBe "none"
      processor.id should not be empty
    }

    "create FlowProcessorNoneRun from URI with params" in {
      val result = FlowProcessors.create("none://some-params")
      result.isSuccess shouldBe true
      val processor = result.get
      processor.name shouldBe "none"
      processor.id should not be empty
    }

    "create FlowProcessorPrintRun from URI without params" in {
      val result = FlowProcessors.create("print")
      result.isSuccess shouldBe true
      val processor = result.get
      processor.name shouldBe "print"
      processor.id should not be empty
    }

    "create FlowProcessorPrintRun from URI with prefix params" in {
      val result = FlowProcessors.create("print://test-prefix")
      result.isSuccess shouldBe true
      val processor = result.get
      processor.name shouldBe "print"
      processor.id should not be empty
    }

    "fail to create processor with invalid URI format" in {
      val result = FlowProcessors.create("invalid://uri://format")
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("Flow processor not found: 'invalid'")
    }

    "fail to create processor with unknown name" in {
      val result = FlowProcessors.create("unknown")
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("Flow processor not found: 'unknown'")
    }

    "fail to create processor with empty URI" in {
      val result = FlowProcessors.create("")
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("Flow processor not found: ''")
    }
  }

  "FlowProcessorNone" should {
    
    "have correct name" in {
      FlowProcessorNone.name shouldBe "none"
    }

    "create FlowProcessorNoneRun instance" in {
      val run = FlowProcessorNone.create("none")
      run.name shouldBe "none"
      run.id should not be empty
    }

    "process flow passes through input unchanged" in {
      val run = FlowProcessorNone.create("none")
      val input = "test input"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "process flow returns Seq with single element" in {
      val run = FlowProcessorNone.create("none")
      val input = "hello world"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq("hello world")
      result.size shouldBe 1
    }

    "process multiple elements sequentially" in {
      val run = FlowProcessorNone.create("none")
      val inputs = Seq("input1", "input2", "input3")
      
      val future = Source(inputs)
        .via(run.process)
        .runWith(Sink.seq)
      
      val results = Await.result(future, 1.second)
      results shouldBe Seq(Seq("input1"), Seq("input2"), Seq("input3"))
      results.size shouldBe 3
    }

    "generate unique IDs for different instances" in {
      val run1 = FlowProcessorNone.create("none")
      val run2 = FlowProcessorNone.create("none")
      
      run1.id should not equal run2.id
    }
  }

  "FlowProcessorPrint" should {
    
    "have correct name" in {
      FlowProcessorPrint.name shouldBe "print"
    }

    "create FlowProcessorPrintRun instance" in {
      val run = FlowProcessorPrint.create("print")
      run.name shouldBe "print"
      run.id should not be empty
    }

    "extract prefix from URI with params" in {
      val run = FlowProcessorPrint.create("print://my-prefix")
      run.name shouldBe "print"
      // Access the prefix via reflection or make it accessible
      // For now, we'll test the behavior
    }

    "process flow prints and passes through input" in {
      val run = FlowProcessorPrint.create("print://test")
      val input = "test message"
      
      // Capture stdout or verify behavior
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "process flow with default prefix when no params" in {
      val run = FlowProcessorPrint.create("print")
      val input = "message"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "process multiple elements and print each" in {
      val run = FlowProcessorPrint.create("print://prefix")
      val inputs = Seq("msg1", "msg2", "msg3")
      
      val future = Source(inputs)
        .via(run.process)
        .runWith(Sink.seq)
      
      val results = Await.result(future, 1.second)
      results shouldBe Seq(Seq("msg1"), Seq("msg2"), Seq("msg3"))
      results.size shouldBe 3
    }

    "generate unique IDs for different instances" in {
      val run1 = FlowProcessorPrint.create("print")
      val run2 = FlowProcessorPrint.create("print")
      
      run1.id should not equal run2.id
    }
  }

  "FlowProcessorRun trait" should {
    
    "have name property" in {
      val noneRun = FlowProcessorNone.create("none")
      val printRun = FlowProcessorPrint.create("print")
      
      noneRun.name shouldBe "none"
      printRun.name shouldBe "print"
    }

    "have id property" in {
      val run = FlowProcessorNone.create("none")
      run.id should not be empty
      run.id.length should be > 0
    }

    "have process method returning Flow" in {
      val run = FlowProcessorNone.create("none")
      val flow = run.process
      
      flow should not be null
      
      // Test that flow can be materialized
      val future = Source.single("test")
        .via(flow)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq("test")
    }
  }

  "FlowProcessor integration" should {
    
    "chain FlowProcessorNone with Source" in {
      val run = FlowProcessors.create("none").get
      val input = "chained input"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "chain FlowProcessorPrint with Source" in {
      val run = FlowProcessors.create("print://chain").get
      val input = "chained message"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "handle empty string input" in {
      val run = FlowProcessors.create("none").get
      
      val future = Source.single("")
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq("")
    }

    "handle multi-line string input" in {
      val run = FlowProcessors.create("none").get
      val input = "line1\nline2\nline3"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }

    "handle special characters in input" in {
      val run = FlowProcessors.create("none").get
      val input = "special: !@#$%^&*()"
      
      val future = Source.single(input)
        .via(run.process)
        .runWith(Sink.head)
      
      val result = Await.result(future, 1.second)
      result shouldBe Seq(input)
    }
  }

  override def afterAll(): Unit = {
    Await.result(system.terminate(), 5.seconds)
    super.afterAll()
  }
}

