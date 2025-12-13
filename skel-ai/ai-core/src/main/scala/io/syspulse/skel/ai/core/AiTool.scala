package io.syspulse.skel.ai.core

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

case class AiTool(
  `type`:String,
  name: Option[String] = None,
  description: Option[String] = None,
  parameters: Option[Map[String, Any]] = None,
  strict: Option[Boolean] = None
)
