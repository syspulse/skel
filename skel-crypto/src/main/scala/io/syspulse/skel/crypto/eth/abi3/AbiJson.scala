package io.syspulse.skel.crypto.eth.abi3

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object AbiJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_abi_typ = jsonFormat2(AbiType)
  implicit val jf_abi_def = jsonFormat8(AbiDef)
  
}
