package io.syspulse.skel.ai.mcp

import com.typesafe.scalalogging.Logger
import spray.json._

/** Echoes the `message` string argument. */
final case class EchoMcpTool() extends McpTool {
  val log = Logger(this.getClass)

  override def name: String = "echo"

  override def description: String = "Echoes back the provided message"

  override def inputSchema: JsObject = JsObject(
    "type"       -> JsString("object"),
    "properties" -> JsObject(
      "message" -> JsObject(
        "type"        -> JsString("string"),
        "description" -> JsString("The message to echo")
      )
    ),
    "required" -> JsArray(JsString("message"))
  )

  override def call(arguments: JsValue): Either[JsonRpcError, JsValue] = {
    log.info(s"[TOOL]: ${arguments}")
    arguments.asJsObject.fields.get("message") match {
      case Some(JsString(msg)) =>
        Right(JsObject("content" -> JsArray(
          JsObject("type" -> JsString("text"), "text" -> JsString(s"Echo: $msg"))
        )))
      case _ => Left(JsonRpcError(-32602, "Missing or invalid 'message' argument"))
    }
  }
}

/** Adds two numbers `a` and `b`. */
final case class AddMcpTool() extends McpTool {
  val log = Logger(this.getClass)

  override def name: String = "add"

  override def description: String = "Adds two numbers together"

  override def inputSchema: JsObject = JsObject(
    "type"       -> JsString("object"),
    "properties" -> JsObject(
      "a" -> JsObject("type" -> JsString("number"), "description" -> JsString("First number")),
      "b" -> JsObject("type" -> JsString("number"), "description" -> JsString("Second number"))
    ),
    "required" -> JsArray(JsString("a"), JsString("b"))
  )

  override def call(arguments: JsValue): Either[JsonRpcError, JsValue] = {
    log.info(s"[TOOL]: ${arguments}")

    val fields = arguments.asJsObject.fields
    (fields.get("a"), fields.get("b")) match {
      case (Some(JsNumber(a)), Some(JsNumber(b))) =>
        val sum = a + b
        Right(JsObject("content" -> JsArray(
          JsObject("type" -> JsString("text"), "text" -> JsString(s"$a + $b = $sum"))
        )))
      case _ => Left(JsonRpcError(-32602, "Arguments 'a' and 'b' must be numbers"))
    }
  }
}
