package io.syspulse.skel.service

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success
import scala.util.Random
import scala.collection.SortedSet

import spray.json._

import io.syspulse.skel.service.JsonCommon

case class Data(ts:Long,data:String = Random.nextString(3)) extends Ordered[Data] {
  def compare (that: Data):Int = {
    if (this.ts == that.ts)
      0
    else if (this.ts > that.ts)
      1
    else
      -1
  }
}

object DataJson extends JsonCommon with CollectionFormats {
  import DefaultJsonProtocol._

  implicit val jf_sc = jsonFormat2(Data.apply _)
}

class JsonCommonSpec extends AnyWordSpec with Matchers {
  import JsonCommon._
  import DefaultJsonProtocol._
  import DataJson._

  "JsonCommon" should {
    "parse SortedSet' to Json" in {
      val ss1 = SortedSet(Data(100),Data(1),Data(20))
      info(s"${ss1}")

      val json = ss1.toJson.toString
      info(s"${json}")
      // ss1.toList should === List(ss1())

      val ss2 = json.parseJson.convertTo[SortedSet[Data]]
      info(s"${ss2}")

      ss1 === ss2
    }
  }
}
