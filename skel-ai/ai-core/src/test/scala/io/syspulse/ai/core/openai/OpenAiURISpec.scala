package io.syspulse.skel.ai.core.openai

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.syspulse.skel.util.Util
import scala.util.Success

class OpenAiURISpec extends AnyWordSpec with Matchers {
  
  "OpenAiURI" should {
    
    "parse 'openai://'" in {
      val u = OpenAiURI("openai://")
      u.apiKey should === ("")
      u.model should === (Some("gpt-4o-mini"))
      u.ops should === (Map())
    }

    "parse 'openai://gpt-4o-mini'" in {
      val u = OpenAiURI("openai://gpt-4o-mini")
      u.apiKey should === ("")
      u.model should === (Some("gpt-4o-mini"))
      u.ops should === (Map())
    }

    "parse 'openai://gpt-4o-mini?apiKey=xxx'" in {
      val u = OpenAiURI("openai://gpt-4o-mini?apiKey=xxx")
      u.apiKey should === ("xxx")
      u.model should === (Some("gpt-4o-mini"))
      u.ops should === (Map("apiKey" -> "xxx"))
    }

    "parse 'openai://API_KEY@gpt-4o-mini'" in {
      val u = OpenAiURI("openai://API_KEY@gpt-4o-mini")
      u.apiKey should === ("API_KEY")
      u.model should === (Some("gpt-4o-mini"))
      u.ops should === (Map())
    }

    "parse 'openai://gpt-4o-mini?vdb=VDB-1'" in {
      val u = OpenAiURI("openai://gpt-4o-mini?vdb=VDB-1")
      u.apiKey should === ("")
      u.model should === (Some("gpt-4o-mini"))
      u.vdb should === (Some("VDB-1"))
    }

    "parse 'openai://gpt-4o?aid=${AID_1}'" in {
      val u = OpenAiURI("openai://gpt-4o?aid=${AID_1}")
      u.apiKey should === ("")
      u.model should === (Some("gpt-4o"))
      u.aid should === (Some(""))
    }
 
  }
}
