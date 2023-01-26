package io.syspulse.skel.crypto.eth.abi

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object AbiSignatureJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_es = jsonFormat3(EventSignature)
  implicit val jf_fs = jsonFormat3(FunSignature)
}
