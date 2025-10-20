package io.syspulse.skel.ai.core

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.syspulse.skel.ai.core.openai.OpenAiURI

object Providers {
  val OPEN_AI = OpenAiURI.ID
  val OPEN_AI_SID = OpenAiURI.ID
}
