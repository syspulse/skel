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

    "POST with Authorization and Content-Type from HEADER: data keys" in {
      val engine = new ScriptApi(Some("""{"value":"{input}"}"""), Some(postHeadersUri))
      val data = Map(
        s"${ScriptApi.HEADER_PREFIX}Authorization" -> "Bearer test-token-1",
        s"${ScriptApi.HEADER_PREFIX}Content-Type" -> "application/json"
      )
      engine.run("", "hello", data) shouldBe Success(
        """auth=Bearer test-token-1;ct=application/json;body={"value":"hello"}"""
      )
    }

    "POST with Authorization from HttpURI auth segment" in {
      val uri = s"http://POST@uri-auth-token@$baseHost/post-headers"
      val engine = new ScriptApi(Some("{input}"), Some(uri))
      val data = Map(s"${ScriptApi.HEADER_PREFIX}Content-Type" -> "application/json")
      engine.run("", "from-uri", data) shouldBe Success(
        "auth=Bearer uri-auth-token;ct=application/json;body=from-uri"
      )
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

    "run POST api with Authorization and Content-Type headers in flow data" in {
      val api = ScriptFlow.resolve("api", """{"id":"{input}"}""", Some(postHeadersUri)).get
      val flow = new ScriptFlow(Seq(api))
      val data = Map(
        s"${ScriptApi.HEADER_PREFIX}Authorization" -> "Bearer flow-token",
        s"${ScriptApi.HEADER_PREFIX}Content-Type" -> "application/json"
      )
      flow.run("", "item-7", data) shouldBe Success(
        """auth=Bearer flow-token;ct=application/json;body={"id":"item-7"}"""
      )
    }

    "chain POST api to postman-echo then ScriptJQ extract json.value" in {
      val postmanUri = "https://POST@postman-echo.com/post"
      val api = ScriptFlow.resolve("api", """{"value":"{input}"}""", Some(postmanUri)).get
      val jq = new ScriptJQ(Some(".json.value"))
      val flow = new ScriptFlow(Seq(api, jq))

      val inputValue = "skel-api-jq-test"
      val data = Map(
        s"${ScriptApi.HEADER_PREFIX}Content-Type" -> "application/json",
        "timeout" -> 30000L
      )

      val result = flow.run("", inputValue, data)
      result.isSuccess shouldBe true
      result.get should include(inputValue)
    }
  }
}
