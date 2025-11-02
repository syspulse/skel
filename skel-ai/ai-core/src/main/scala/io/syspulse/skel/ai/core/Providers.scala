package io.syspulse.skel.ai.core

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

object Providers {
  val OPEN_AI = OpenAiURI.ID
  val OPEN_AI_SID = OpenAiURI.ID
  val GROK = GrokURI.ID
  val OPEN_ROUTER = OpenRouterURI.ID
  val DEEPSEEK = DeepseekURI.ID
  val CLAUDE = ClaudeURI.ID
  val VENICE = VeniceURI.ID
}
