package io.syspulse.skel.crypto

import scala.util.{Try, Success, Failure}
import scala.io.Source

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import ujson._

class EthTxErrorSpec extends AnyWordSpec with Matchers {

  "Eth.parseTxErrorFromTraceJson" should {

    "extract error from root, output from root, revertReason from root or deepest error (ERR-1.json)" in {
      val json = Source.fromResource("ERR-1.json").mkString
      val parsed = ujson.read(json)
      val traceJson = ujson.write(parsed("result"))
      val result = Eth.parseTxErrorFromTraceJson(traceJson)
      result shouldBe a[Success[_]]
      val (error, output, revertReason) = result.get
      // Error from root; result wrapper has only "calls" so root error is None
      error should === (Some("execution reverted"))
      // Output = root output (wrapper has none)
      output should === (None)
      // revertReason = root revertReason or deepest frame error ("out of gas")
      revertReason shouldEqual Some("out of gas")
    }

    "extract error from root, output only from root (ERR-2.json)" in {
      val json = Source.fromResource("ERR-2.json").mkString
      val parsed = ujson.read(json)
      val traceJson = ujson.write(parsed("result"))
      val result = Eth.parseTxErrorFromTraceJson(traceJson)
      result shouldBe a[Success[_]]
      val (error, output, revertReason) = result.get
      error shouldEqual Some("out of gas: out of gas")
      output shouldBe None
      revertReason shouldEqual Some("out of gas: out of gas")
    }

    "extract deepest error from callTracer trace (ERR-3.json)" in {
      val json = Source.fromResource("ERR-3.json").mkString
      val parsed = ujson.read(json)
      val traceJson = ujson.write(parsed("result"))
      val result = Eth.parseTxErrorFromTraceJson(traceJson)
      result shouldBe a[Success[_]]
      val (error, output, revertReason) = result.get
      error shouldEqual Some("execution reverted")
      // Output and revertReason from root (first call when result is that call)
      output shouldBe defined
      output.get should startWith("0x08c379a0")
      revertReason shouldEqual Some("amount exceeds available balance")
    }

    "extract deepest error from callTracer trace (ERR-4.json)" in {
      val json = Source.fromResource("ERR-4.json").mkString
      val parsed = ujson.read(json)
      val traceJson = ujson.write(parsed("result"))
      val result = Eth.parseTxErrorFromTraceJson(traceJson)
      result shouldBe a[Success[_]]
      val (error, output, revertReason) = result.get
      error shouldEqual Some("execution reverted")
      output shouldBe defined
      output.get should startWith("0xd8139a03")
      revertReason shouldEqual Some("execution reverted")
    }
  }
}
