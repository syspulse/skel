package io.syspulse.skel.ai.provider.grok

import io.syspulse.skel.ai.core.GrokURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class GrokAi(uri:GrokURI) extends OpenAiLike {
  override def getUri():AiURI = uri
}