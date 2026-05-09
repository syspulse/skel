package io.syspulse.skel.ai.provider.venice

import io.syspulse.skel.ai.core.VeniceURI
import io.syspulse.skel.ai.provider.openai.OpenAiLike
import io.syspulse.skel.ai.core.AiURI

class VeniceAi(uri:VeniceURI) extends OpenAiLike {
  override def getUri():AiURI = uri
}