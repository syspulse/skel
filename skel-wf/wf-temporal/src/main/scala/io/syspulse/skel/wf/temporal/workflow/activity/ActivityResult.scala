package io.syspulse.skel.wf.temporal.workflow.activity

import com.fasterxml.jackson.annotation.JsonProperty
import scala.beans.BeanProperty

/**
 * Activity execution result
 *
 * This case class is serialized by Temporal's Jackson serializer
 * and displays properly in Temporal UI Events History
 */
case class ActivityResult(
  @BeanProperty @JsonProperty("configId") configId: Int,
  @BeanProperty @JsonProperty("activityName") activityName: String,
  @BeanProperty @JsonProperty("status") status: String,
  @BeanProperty @JsonProperty("timestamp") timestamp: Long,
  @BeanProperty @JsonProperty("output") output: Map[String, Any]
)

object ActivityResult {
  /**
   * Create success result from spray.json.JsObject output
   */
  def success(configId: Int, activityName: String, output: spray.json.JsObject): ActivityResult = {
    import spray.json._

    // Convert JsObject to Map[String, Any]
    val outputMap = jsObjectToMap(output)

    ActivityResult(
      configId = configId,
      activityName = activityName,
      status = "SUCCESS",
      timestamp = System.currentTimeMillis(),
      output = outputMap
    )
  }

  /**
   * Create success result with empty output
   */
  def success(configId: Int, activityName: String): ActivityResult = {
    ActivityResult(
      configId = configId,
      activityName = activityName,
      status = "SUCCESS",
      timestamp = System.currentTimeMillis(),
      output = Map.empty
    )
  }

  /**
   * Convert spray.json.JsValue to Scala Map/List/primitives
   */
  private def jsValueToAny(jsValue: spray.json.JsValue): Any = {
    import spray.json._

    jsValue match {
      case JsObject(fields) => jsObjectToMap(JsObject(fields))
      case JsArray(elements) => elements.map(jsValueToAny).toList
      case JsString(value) => value
      case JsNumber(value) =>
        // Try to keep as Int if possible, otherwise Double
        if (value.isValidInt) value.intValue
        else if (value.isValidLong) value.longValue
        else value.doubleValue
      case JsBoolean(value) => value
      case JsTrue => true
      case JsFalse => false
      case JsNull => null
    }
  }

  /**
   * Convert spray.json.JsObject to Map[String, Any]
   */
  private def jsObjectToMap(jsObject: spray.json.JsObject): Map[String, Any] = {
    jsObject.fields.map { case (key, value) =>
      key -> jsValueToAny(value)
    }
  }
}
