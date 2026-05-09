package io.syspulse.skel.ai.provider.ika

import io.syspulse.skel.ai.core.IkaURI
import io.syspulse.skel.ai.provider.claude.ClaudeAiLike
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class IkaAi(uri:IkaURI) extends OpenAiLike with ClaudeAiLike {
  override def getUri():AiURI = uri
}