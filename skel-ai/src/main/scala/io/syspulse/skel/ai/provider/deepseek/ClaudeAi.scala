package io.syspulse.skel.ai.provider.deepseek

import io.syspulse.skel.ai.core.DeepseekURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class DeepseekAi(uri:DeepseekURI) extends OpenAiLike {
  override def getUri():AiURI = uri
}