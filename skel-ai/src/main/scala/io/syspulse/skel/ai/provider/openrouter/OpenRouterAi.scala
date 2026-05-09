package io.syspulse.skel.ai.provider.openrouter

import io.syspulse.skel.ai.core.OpenRouterURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class OpenRouterAi(uri:OpenRouterURI) extends OpenAiLike {
  override def getUri():AiURI = uri
}