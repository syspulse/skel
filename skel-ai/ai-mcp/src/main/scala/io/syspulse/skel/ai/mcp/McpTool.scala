package io.syspulse.skel.ai.mcp

import com.typesafe.scalalogging.Logger
import spray.json._

case class JsonRpcError(code: Int, message: String)

/** Pluggable MCP tool: implement `call` and JSON schema metadata for `tools/list`. */
trait McpTool {

  def name: String

  def description: String

  /** JSON Schema object for MCP `inputSchema`. */
  def inputSchema: JsObject

  def call(arguments: JsValue): Either[JsonRpcError, JsValue]

  def toListEntry: JsObject =
    JsObject(
      "name"        -> JsString(name),
      "description" -> JsString(description),
      "inputSchema" -> inputSchema
    )
}

object McpTool {

  def toolListJson(tools: Seq[McpTool]): JsValue =
    JsObject("tools" -> JsArray(tools.map(_.toListEntry): _*))

  def callTool(tools: Seq[McpTool], toolName: String, args: JsValue): Either[JsonRpcError, JsValue] =
    tools.find(_.name == toolName) match {
      case Some(t) => t.call(args)
      case None    => Left(JsonRpcError(-32601, s"Unknown tool: $toolName"))
    }
}
