package io.syspulse.skel.crypto.eth.abi

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object AbiContractJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_ac = jsonFormat3(AbiContract)
}
