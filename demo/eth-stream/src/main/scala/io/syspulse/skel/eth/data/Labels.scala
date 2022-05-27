package io.syspulse.skel.eth.data

import scala.util.Random

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.dsl.JS
import java.time.LocalDateTime
import java.time.ZonedDateTime
import scala.util.Try
import scala.util.Success

case class Label(id:String,typ:String = "",desc:String = "")

object Labels {

  val default = Seq(
    "binance" -> Label("binance","exchange"),
    "whale-1" -> Label("whale-1","whale"),
    "scam" -> Label("scam","scammer","0x0000001"),
    "multisig" -> Label("multisig","Multisig","Gnosis"),
    "hw" -> Label("hw","HSM","Hardware Wallet")
  )

  var labels = Map[String,Label]() ++ default

  def +(label:Label) = {
    labels = labels + (label.id -> label)
  }

}


