package io.syspulse.skel.geo

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util

class GeohashSpec extends WordSpec with Matchers {

  "GeohashSpec" should {
    "61.702662 7.632055 -> u4vrj8tkpcbe" in {
      val g1 = Geohash.encode(61.702662,7.632055)
      g1 should === ("u4vrj8tkpcbe")
    }

    "50.685324,30.463904 -> u9j8m4zpkmjn" in {
      val g1 = Geohash.encode(50.685324,30.463904)
      g1 should === ("u9j8m4zpkmjn")
    }

    "51.196719,29.485967 -> u9hgqbp48b4s" in {
      val g1 = Geohash.encode(51.196719,29.485967)
      g1 should === ("u9hgqbp48b4s")
    }
  }
}
