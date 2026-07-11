package io.syspulse.skel.script

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.util.Success

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.SystemMaterializer

class ScriptApiSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  sys.props("polyglot.engine.WarnInterpreterOnly") = "false"

  private val testSystem = ActorSystem("ScriptApiSpec")
  private implicit val ec: ExecutionContext = testSystem.dispatcher
  private implicit val mat = SystemMaterializer(testSystem).materializer

  private var port: Int = _
  private var binding: Http.ServerBinding = _

  private def baseHost = s"127.0.0.1:$port"
  private def getUri = s"http://GET@$baseHost/get"
  private def postUri = s"http://POST@$baseHost/post"
  private def postHeadersUri = s"http://POST@$baseHost/post-headers"
  private def postXHeaderUri = s"http://POST@$baseHost/post-xheader"
  private def postEchoUri = s"http://POST@$baseHost/post-echo"

  private val route =
    path("get") {
      get {
        complete(StatusCodes.OK, HttpEntity(ContentTypes.`text/plain(UTF-8)`, "get-ok"))
      }
    } ~
    path("post") {
      post {
        entity(as[String]) { body =>
          val payload = if (body.isEmpty) "post-empty" else s"post:$body"
          complete(StatusCodes.OK, HttpEntity(ContentTypes.`text/plain(UTF-8)`, payload))
        }
      }
    } ~
    path("post-headers") {
      post {
        extractRequest { req =>
          entity(as[String]) { body =>
            val auth = req.headers.find(_.is("authorization")).map(_.value()).getOrElse("")
            val ct =
              if (req.entity.contentType == ContentTypes.NoContentType) ""
              else req.entity.contentType.mediaType.value
            complete(
              StatusCodes.OK,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"auth=$auth;ct=$ct;body=$body")
            )
          }
        }
      }
    } ~
    path("post-xheader") {
      post {
        extractRequest { req =>
          entity(as[String]) { body =>
            val x = req.headers.find(_.is("x-custom")).map(_.value()).getOrElse("")
            complete(
              StatusCodes.OK,
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, s"x=$x;body=$body")
            )
          }
        }
      }
    } ~
    path("post-echo") {
      // mimics postman-echo: wraps the posted JSON body under a "json" field
      post {
        entity(as[String]) { body =>
          complete(
            StatusCodes.OK,
            HttpEntity(ContentTypes.`application/json`, s"""{"json":$body}""")
          )
        }
      }
    }

  override def beforeAll(): Unit = {
    binding = Await.result(Http()(testSystem).bindAndHandle(route, "127.0.0.1", 0), 10.seconds)
    port = binding.localAddress.getPort
  }

  override def afterAll(): Unit = {
    Await.result(binding.unbind(), 10.seconds)
    Await.result(testSystem.terminate(), 10.seconds)
  }

  "ScriptApi" should {
    "have id api" in {
      new ScriptApi(None, Some(getUri)).getId() shouldBe "api"
    }

    "GET without body returns response" in {
      val engine = new ScriptApi(None, Some(getUri))
      engine.run("", "ignored-input", Map.empty) shouldBe Success("get-ok")
    }

    "POST without body sends empty entity" in {
      val engine = new ScriptApi(None, Some(postUri))
      engine.run("", "ignored-input", Map.empty) shouldBe Success("post-empty")
    }

    "POST with body from constructor substitutes {input}" in {
      val engine = new ScriptApi(Some("""{"value":"{input}"}"""), Some(postUri))
      engine.run("", "hello", Map.empty) shouldBe Success("""post:{"value":"hello"}""")
    }

    "POST with body from run src when body0 is empty" in {
      val engine = new ScriptApi(None, Some(postUri))
      engine.run("""{"from":"{input}"}""", "world", Map.empty) shouldBe Success("""post:{"from":"world"}""")
    }

    "substitute extra data keys in POST body" in {
      val engine = new ScriptApi(Some("""{"in":"{input}","role":"{role}"}"""), Some(postUri))
      engine.run("", "data-1", Map("role" -> "admin")) shouldBe Success("""post:{"in":"data-1","role":"admin"}""")
    }

    "resolve via ScriptFlow.resolve for GET" in {
      val script = ScriptFlow.resolve("api", "", Some(getUri))
      script.isSuccess shouldBe true
      script.get.run("", "x", Map.empty) shouldBe Success("get-ok")
    }

    "resolve via ScriptFlow.resolve for POST with body" in {
      val script = ScriptFlow.resolve("api", "{input}", Some(postUri))
      script.isSuccess shouldBe true
      script.get.run("", "payload-42", Map.empty) shouldBe Success("post:payload-42")
    }

    "POST with Authorization and Content-Type from HEADERS section" in {
      val body =
        """HEADERS
          |Authorization: Bearer test-token-1
          |Content-Type: application/json
          |
          |BODY
          |{"value":"{input}"}""".stripMargin
      val engine = new ScriptApi(Some(body), Some(postHeadersUri))
      engine.run("", "hello", Map.empty) shouldBe Success(
        """auth=Bearer test-token-1;ct=application/json;body={"value":"hello"}"""
      )
    }

    "POST with Authorization from HttpURI auth segment" in {
      val uri = s"http://POST@uri-auth-token@$baseHost/post-headers"
      val body =
        """HEADERS
          |Content-Type: application/json
          |
          |BODY
          |{input}""".stripMargin
      val engine = new ScriptApi(Some(body), Some(uri))
      engine.run("", "from-uri", Map.empty) shouldBe Success(
        "auth=Bearer uri-auth-token;ct=application/json;body=from-uri"
      )
    }

    "POST with HEADERS and BODY sections in body0" in {
      val body =
        """HEADERS
          |Content-Type: application/json
          |Authorization: Bearer section-token
          |
          |BODY
          |{"value":"{input}"}""".stripMargin
      val engine = new ScriptApi(Some(body), Some(postHeadersUri))
      engine.run("", "hello", Map.empty) shouldBe Success(
        """auth=Bearer section-token;ct=application/json;body={"value":"hello"}"""
      )
    }

    "substitute {input} placeholder inside HEADERS section" in {
      val body =
        """HEADERS
          |X-Custom: tok-{input}
          |
          |BODY
          |payload-{input}""".stripMargin
      val engine = new ScriptApi(Some(body), Some(postXHeaderUri))
      engine.run("", "42", Map.empty) shouldBe Success("x=tok-42;body=payload-42")
    }

    "treat entire source as BODY when no HEADERS section present" in {
      val engine = new ScriptApi(Some("""{"value":"{input}"}"""), Some(postUri))
      engine.run("", "no-headers", Map.empty) shouldBe Success(
        """post:{"value":"no-headers"}"""
      )
    }

    "support HEADERS section with empty BODY" in {
      val body =
        """HEADERS
          |X-Custom: only-headers
          |
          |BODY
          |""".stripMargin
      val engine = new ScriptApi(Some(body), Some(postXHeaderUri))
      engine.run("", "ignored", Map.empty) shouldBe Success("x=only-headers;body=")
    }
  }

  "ScriptApi.parseSections" should {
    "return all text as body when no HEADERS marker" in {
      val (headers, body) = ScriptApi.parseSections("""{"value":"{input}"}""")
      headers shouldBe empty
      body shouldBe Some("""{"value":"{input}"}""")
    }

    "parse HEADERS and BODY sections" in {
      val text =
        """HEADERS
          |a: 1
          |b: 2
          |
          |BODY
          |{input}""".stripMargin
      val (headers, body) = ScriptApi.parseSections(text)
      headers shouldBe Seq("a" -> "1", "b" -> "2")
      body shouldBe Some("{input}")
    }

    "parse HTTP-style ':' separated headers" in {
      val text =
        """HEADERS
          |Content-Type: application/json
          |Authorization: Bearer TOKEN_0000000000000001
          |
          |BODY
          |{"data":"0x1"}""".stripMargin
      val (headers, body) = ScriptApi.parseSections(text)
      headers shouldBe Seq(
        "Content-Type" -> "application/json",
        "Authorization" -> "Bearer TOKEN_0000000000000001"
      )
      body shouldBe Some("""{"data":"0x1"}""")
    }

    "split header on the first ':' only (value may contain ':')" in {
      val text =
        """HEADERS
          |X-Url: https://example.com/path
          |
          |BODY
          |{input}""".stripMargin
      val (headers, body) = ScriptApi.parseSections(text)
      headers shouldBe Seq("X-Url" -> "https://example.com/path")
      body shouldBe Some("{input}")
    }

    "ignore header lines using '=' (only ':' is supported)" in {
      val text =
        """HEADERS
          |a = 1
          |
          |BODY
          |{input}""".stripMargin
      val (headers, body) = ScriptApi.parseSections(text)
      headers shouldBe empty
      body shouldBe Some("{input}")
    }

    "return no body when BODY section is empty" in {
      val text =
        """HEADERS
          |a: 1
          |
          |BODY
          |""".stripMargin
      val (headers, body) = ScriptApi.parseSections(text)
      headers shouldBe Seq("a" -> "1")
      body shouldBe None
    }

    "return empty for blank input" in {
      ScriptApi.parseSections("") shouldBe (Seq.empty, None)
    }
  }

  "ScriptFlow with ScriptApi" should {
    "run GET api as single step" in {
      val api = ScriptFlow.resolve("api", "", Some(getUri)).get
      val flow = new ScriptFlow(Seq(api))
      flow.run("", "ignored", Map.empty) shouldBe Success("get-ok")
    }

    "run POST api without body as single step" in {
      val api = ScriptFlow.resolve("api", "", Some(postUri)).get
      val flow = new ScriptFlow(Seq(api))
      flow.run("", "ignored", Map.empty) shouldBe Success("post-empty")
    }

    "run POST api with body as single step" in {
      val api = ScriptFlow.resolve("api", """{"msg":"{input}"}""", Some(postUri)).get
      val flow = new ScriptFlow(Seq(api))
      flow.run("", "flow-input", Map.empty) shouldBe Success("""post:{"msg":"flow-input"}""")
    }

    "chain ScriptJS then POST api with body from previous step output" in {
      val api = ScriptFlow.resolve("api", """{"name":"{input}"}""", Some(postUri)).get
      val js = new ScriptJS(Some("JSON.parse(input).name"))
      val flow = new ScriptFlow(Seq(js, api))

      val json = """{"name":"Alice","age":30}"""
      flow.run("", json, Map.empty) shouldBe Success("""post:{"name":"Alice"}""")
    }

    "chain POST api then ScriptRegexp on response" in {
      val api = ScriptFlow.resolve("api", "", Some(getUri)).get
      val regexp = new ScriptRegexp(Some(".*get-ok.*"))
      val flow = new ScriptFlow(Seq(api, regexp))

      flow.run("", "ignored", Map.empty).isSuccess shouldBe true
    }

    "chain POST api with body then ScriptJS transform" in {
      val api = ScriptFlow.resolve("api", "{input}", Some(postUri)).get
      val js = new ScriptJS(Some("input.replace('post:', '')"))
      val flow = new ScriptFlow(Seq(api, js))

      flow.run("", "js-chain", Map.empty) shouldBe Success("js-chain")
    }

    "exec async POST api in flow" in {
      val api = ScriptFlow.resolve("api", """{"x":"{input}"}""", Some(postUri)).get
      val flow = new ScriptFlow(Seq(api))
      val result = Await.result(flow.exec("", "async-val", Map.empty), 10.seconds)
      result shouldBe """post:{"x":"async-val"}"""
    }

    "run POST api with Authorization and Content-Type headers from HEADERS section in flow" in {
      val body =
        """HEADERS
          |Authorization: Bearer flow-token
          |Content-Type: application/json
          |
          |BODY
          |{"id":"{input}"}""".stripMargin
      val api = ScriptFlow.resolve("api", body, Some(postHeadersUri)).get
      val flow = new ScriptFlow(Seq(api))
      flow.run("", "item-7", Map.empty) shouldBe Success(
        """auth=Bearer flow-token;ct=application/json;body={"id":"item-7"}"""
      )
    }

    "chain POST api (echo) then ScriptJQ extract json.value" in {
      val body =
        """HEADERS
          |Content-Type: application/json
          |
          |BODY
          |{"value":"{input}"}""".stripMargin
      val api = ScriptFlow.resolve("api", body, Some(postEchoUri)).get
      val jq = new ScriptJQ(Some(".json.value"))
      val flow = new ScriptFlow(Seq(api, jq))

      val inputValue = "skel-api-jq-test"
      val data = Map[String,Any]("timeout" -> 30000L)

      val result = flow.run("", inputValue, data)
      result.isSuccess shouldBe true
      result.get should include(inputValue)
    }
  }

  // Reproduces the exact sequence used by ExplainRegistry.RunExplain:
  //   val engines = rule.scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
  //   val scriptFlow = ScriptFlow.build(engines)
  //   scriptFlow.run("", input, dataMap)
  "ScriptFlow mimicking ExplainRegistry.RunExplain" should {
    // local stand-in for io.syspulse.skel.explain.ExplainScript (skel-explain depends on skel-script, not vice versa)
    case class RuleScript(typ: String, src: String, opts: Option[String] = None)

    "execute ScriptApi passing parsed HEADERS and BODY sections" in {
      val body =
        """HEADERS
          |Authorization: Bearer explain-token
          |Content-Type: application/json
          |
          |BODY
          |{"value":"{input}"}""".stripMargin

      val scripts = Seq(RuleScript("api", body, Some(postHeadersUri)))

      val engines = scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
      val scriptFlow = ScriptFlow.build(engines)

      val input = "hello-explain"
      val dataMap: Map[String, Any] = Map(
        "oid"   -> "",
        "rid"   -> "rule-1",
        "sid"   -> "",
        "style" -> "short"
      )

      scriptFlow.run("", input, dataMap) shouldBe Success(
        """auth=Bearer explain-token;ct=application/json;body={"value":"hello-explain"}"""
      )
    }

    "substitute dataMap keys inside HEADERS and BODY sections" in {
      val body =
        """HEADERS
          |X-Custom: {style}
          |
          |BODY
          |{"rid":"{rid}","in":"{input}"}""".stripMargin

      val scripts = Seq(RuleScript("api", body, Some(postXHeaderUri)))

      val engines = scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
      val scriptFlow = ScriptFlow.build(engines)

      val dataMap: Map[String, Any] = Map(
        "oid"   -> "",
        "rid"   -> "rule-7",
        "sid"   -> "",
        "style" -> "verbose"
      )

      scriptFlow.run("", "payload", dataMap) shouldBe Success(
        """x=verbose;body={"rid":"rule-7","in":"payload"}"""
      )
    }

    "execute ScriptApi (with HEADERS/BODY) chained with a downstream engine" in {
      val body =
        """HEADERS
          |Content-Type: application/json
          |
          |BODY
          |{"value":"{input}"}""".stripMargin

      val scripts = Seq(
        RuleScript("api", body, Some(postEchoUri)),
        RuleScript("jq", ".json.value", None)
      )

      val engines = scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
      val scriptFlow = ScriptFlow.build(engines)

      val dataMap: Map[String, Any] = Map(
        "oid"     -> "",
        "rid"     -> "rule-9",
        "sid"     -> "",
        "style"   -> "short",
        "timeout" -> 30000L
      )

      val result = scriptFlow.run("", "explain-chain", dataMap)
      result.isSuccess shouldBe true
      result.get should include("explain-chain")
    }

    "execute api against real echo endpoint (beeceptor)" in {
      val src =
        """HEADERS
          |Content-Type: application/json
          |Authorization: Bearer TOKEN_0000000000000001
          |Test: 11111111
          |
          |BODY
          |{"data":"0x1"}""".stripMargin

      val scripts = Seq(
        RuleScript("api", src, Some("https://POST@echo.free.beeceptor.com"))
      )

      val engines = scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
      val scriptFlow = ScriptFlow.build(engines)

      val dataMap: Map[String, Any] = Map(
        "oid"     -> "",
        "rid"     -> "rule-echo",
        "sid"     -> "",
        "style"   -> "short",
        "timeout" -> 30000L
      )

      val result = scriptFlow.run("", "ignored-input", dataMap)
      result.isSuccess shouldBe true
      result.get should include("0x1")
      result.get should include("TOKEN_0000000000000001")
      result.get should include("11111111")
    }
  }
}
