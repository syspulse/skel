package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class WsURISpec extends AnyWordSpec with Matchers {
  
  "WsURI" should {
    "parse basic websocket URL without options" in {
      val uri = WsURI("ws://localhost:8080/ws")
      uri.url should === ("ws://localhost:8080/ws")
      uri.ops should === (Map())
      uri.auth should === (None)
    }

    "parse websocket URL with single option" in {
      val uri = WsURI("ws://localhost:8080/ws?auth=token123")
      uri.url should === ("ws://localhost:8080/ws")
      uri.ops should === (Map("auth" -> "token123"))
      uri.auth should === (Some("token123"))
    }

    "parse websocket URL with multiple options" in {
      val uri = WsURI("ws://localhost:8080/ws?auth=token123&header1=value1&header2=value2")
      uri.url should === ("ws://localhost:8080/ws")
      uri.ops should === (Map(
        "auth" -> "token123",
        "header1" -> "value1",
        "header2" -> "value2"
      ))
      uri.auth should === (Some("token123"))
    }

    "parse websocket URL with empty option value" in {
      val uri = WsURI("ws://localhost:8080/ws?header1")
      uri.url should === ("ws://localhost:8080/ws")
      uri.ops should === (Map("header1" -> ""))
    }

    "handle empty URL by returning default values" in {
      val uri = WsURI("")
      uri.url should === ("")
      uri.ops should === (Map())
    }

    "handle malformed URL by returning default values" in {
      val uri = WsURI("malformed?key=value")
      uri.url should === ("malformed")
      uri.ops should === (Map("key" -> "value"))
    }

    "parse URL with different host and port" in {
      val uri = WsURI("ws://example.com:9090/websocket")
      uri.url should === ("ws://example.com:9090/websocket")
      uri.ops should === (Map())
    }

    "parse URL with multiple path segments" in {
      val uri = WsURI("ws://localhost:8080/api/v1/ws?auth=token")
      uri.url should === ("ws://localhost:8080/api/v1/ws")
      uri.ops should === (Map("auth" -> "token"))
    }
  }
} 