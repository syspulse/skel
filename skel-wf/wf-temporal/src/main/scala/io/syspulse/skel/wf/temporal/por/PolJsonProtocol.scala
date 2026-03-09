package io.syspulse.skel.wf.temporal.por

import spray.json._
import java.util.UUID

object PolJsonProtocol extends DefaultJsonProtocol {

  implicit object UUIDFormat extends JsonFormat[UUID] {
    def write(uuid: UUID): JsValue = JsString(uuid.toString)
    def read(value: JsValue): UUID = value match {
      case JsString(uuid) => UUID.fromString(uuid)
      case _ => throw new DeserializationException("Expected UUID as JsString")
    }
  }

  implicit object BigIntFormat extends JsonFormat[BigInt] {
    def write(bigInt: BigInt): JsValue = JsString(bigInt.toString)
    def read(value: JsValue): BigInt = value match {
      case JsString(s) => BigInt(s)
      case JsNumber(n) => n.toBigInt
      case _ => throw new DeserializationException("Expected BigInt as JsString or JsNumber")
    }
  }

  implicit val liabilityFormat: RootJsonFormat[Liability] = jsonFormat3(Liability)
  implicit val polFileDataFormat: RootJsonFormat[PolFileData] = jsonFormat5(PolFileData)
}
