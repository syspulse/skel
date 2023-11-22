package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthTxSpec extends AnyWordSpec with Matchers with TestData {

  "EthTxSpec" should {
    
    "sign Tx without data" in {
      val sig = Eth.signTx(
        //sk1,
        "0xd0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc",
        "0x3c5f859ee194b293871cf01a6eb072edd2e46a22",
        value = "0",
        nonce = 0,
        gasPrice = "20.0",
        gasTip = "5.0",
        gasLimit = 21000L,
        data = None,
        chainId = 11155111)
      info(s"sig=${sig}")
      sig should === ("0x02f86f83aa36a78085012a05f2008504a817c800825208943c5f859ee194b293871cf01a6eb072edd2e46a228080c080a0a9f1e4f8ecf54e57c1e192d025c82b56946986b8e0eccc2ad2f008c8f5669e5fa030eb3bf72f25a33a89c77c70c96cc9bc8452c19f16bf137454fa95d9606019bc")
    }

    "sign Tx with data" in {
      val sig = Eth.signTx(
        //sk1,
        "0xd0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc",
        "0x3c5f859ee194b293871cf01a6eb072edd2e46a22",
        value = "0",
        nonce = 0,
        gasPrice = "20.0",
        gasTip = "5.0",
        gasLimit = 21000L,
        data = Some("0x11"),
        chainId = 11155111)
      info(s"sig=${sig}")
      sig should === ("0x02f86f83aa36a78085012a05f2008504a817c800825208943c5f859ee194b293871cf01a6eb072edd2e46a228011c080a052f893d59dc1c574f1a74421a2b4a2639a495af47bee01a3afa9006c3b2b9d13a015065a6a1accf88bce154326bdeae24a1ad43d77b8fdb899edb56500765785ad")
    }
    
  }

}
