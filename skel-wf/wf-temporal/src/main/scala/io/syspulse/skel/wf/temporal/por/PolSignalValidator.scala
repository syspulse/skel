package io.syspulse.skel.wf.temporal.por

import spray.json._
import com.typesafe.scalalogging.Logger

/**
 * Validates and parses PoL signal data
 */
object PolSignalValidator {
  private val log = Logger(getClass.getName)

  /**
   * Validate and parse signal data
   *
   * @param data Signal data as JsObject
   * @param wid Workflow ID for logging
   * @return Some(PolFileData) if valid, None if invalid
   */
  def validateAndParse(data: JsObject, wid: String): Option[PolFileData] = {
    try {
      import PolJsonProtocol._

      val polFileData = data.convertTo[PolFileData]

      // Validate data (must have liabilities, signature, etc.)
      if (polFileData.liabilities.isEmpty) {
        log.error(s"$wid PoL: Invalid signal data - no liabilities")
        None
      } else if (polFileData.signature.isEmpty || polFileData.publicKey.isEmpty) {
        log.error(s"$wid PoL: Invalid signal data - missing signature/publicKey")
        None
      } else {
        // Valid data!
        log.info(s"$wid PoL: Valid signal data with ${polFileData.liabilities.size} liabilities")
        Some(polFileData)
      }
    } catch {
      case e: Exception =>
        log.error(s"$wid PoL: Failed to parse signal data: ${e.getMessage}")
        None
    }
  }

  /**
   * Create PolOutput from validated PolFileData
   */
  def createOutput(data: PolFileData): PolOutput = {
    PolOutput(
      ts = data.timestamp,
      liabilities = data.liabilities,
      signature = data.signature,
      signatureType = data.signatureType,
      publicKey = data.publicKey
    )
  }
}
