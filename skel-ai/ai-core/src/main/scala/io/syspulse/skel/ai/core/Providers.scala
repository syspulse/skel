package io.syspulse.skel.ai.core

import scala.util.{Try,Success,Failure}
import scala.collection.immutable

import io.syspulse.skel.ai.core.{OpenAiURI,GrokURI,GeminiURI,OpenRouterURI,DeepseekURI,ClaudeURI,VeniceURI}

object Providers {
  val OPEN_AI = OpenAiURI.ID
  val OPEN_AI_SID = OpenAiURI.ID
  val GROK = GrokURI.ID
  val GEMINI = GeminiURI.ID
  val OPEN_ROUTER = OpenRouterURI.ID
  val DEEPSEEK = DeepseekURI.ID
  val CLAUDE = ClaudeURI.ID
  val VENICE = VeniceURI.ID
  val MIRROR = MirrorURI.ID
}
