package io.syspulse.skel.ai.provider.gemini

import io.syspulse.skel.ai.core.GeminiURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class GeminiAi(uri:GeminiURI) extends OpenAiLike {
  override def getUri():AiURI = uri
}