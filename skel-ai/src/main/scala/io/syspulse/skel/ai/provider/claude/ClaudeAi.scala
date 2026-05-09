package io.syspulse.skel.ai.provider.claude

import io.syspulse.skel.ai.core.ClaudeURI
import io.syspulse.skel.ai.core.AiURI

class ClaudeAi(uri: ClaudeURI) extends ClaudeAiLike {
  override def getUri(): AiURI = uri
}
