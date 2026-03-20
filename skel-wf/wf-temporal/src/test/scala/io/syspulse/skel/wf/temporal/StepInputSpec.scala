package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._
import io.syspulse.skel.wf.temporal.por.{StepInput, WorkflowInputs}
import io.syspulse.skel.wf.temporal.workflow.server.{StepInputReq, StepInputRes, WorkflowJson}

/**
 * Test suite for Step Input API
 */
class StepInputSpec extends AnyWordSpec with Matchers {

  import WorkflowJson._

  "StepInput" should {

    "serialize and deserialize correctly" in {
      val stepInput = StepInput(
        stepId = "pol",
        data = JsObject(
          "liabilities" -> JsArray(
            JsObject("userId" -> JsString("user1"), "amount" -> JsNumber(1000)),
            JsObject("userId" -> JsString("user2"), "amount" -> JsNumber(2000))
          )
        )
      )

      val json = stepInput.toJson
      val deserialized = json.convertTo[StepInput]

      deserialized.stepId shouldBe "pol"
      deserialized.data shouldBe a[JsObject]
      deserialized.metadata shouldBe Map.empty
      deserialized.timestamp should be > 0L
    }

    "support custom metadata" in {
      val stepInput = StepInput(
        stepId = "poo",
        data = JsObject("wallets" -> JsArray()),
        metadata = Map("source" -> "api", "version" -> "1.0")
      )

      stepInput.metadata("source") shouldBe "api"
      stepInput.metadata("version") shouldBe "1.0"
    }
  }

  "WorkflowInputs" should {

    "update and retrieve step inputs" in {
      var inputs = WorkflowInputs()

      val polInput = StepInput("pol", JsObject("data" -> JsString("pol-data")))
      val porInput = StepInput("por", JsObject("data" -> JsString("por-data")))

      inputs = inputs.update(polInput)
      inputs = inputs.update(porInput)

      inputs.get("pol") shouldBe Some(polInput)
      inputs.get("por") shouldBe Some(porInput)
      inputs.get("nonexistent") shouldBe None
    }

    "overwrite existing inputs with same stepId" in {
      var inputs = WorkflowInputs()

      val input1 = StepInput("pol", JsObject("version" -> JsNumber(1)))
      val input2 = StepInput("pol", JsObject("version" -> JsNumber(2)))

      inputs = inputs.update(input1)
      inputs = inputs.update(input2)

      inputs.get("pol") shouldBe Some(input2)
      inputs.inputs.size shouldBe 1
    }

    "convert step input data to specific types" in {
      case class TestData(value: String)
      implicit val testDataFormat: RootJsonFormat[TestData] = jsonFormat1(TestData)

      var inputs = WorkflowInputs()
      val testInput = StepInput("test", TestData("hello").toJson)
      inputs = inputs.update(testInput)

      val retrieved = inputs.getAs[TestData]("test")
      retrieved shouldBe Some(TestData("hello"))
    }

    "serialize and deserialize correctly" in {
      val polInput = StepInput("pol", JsObject("data" -> JsString("pol-data")))
      val porInput = StepInput("por", JsObject("data" -> JsString("por-data")))

      var inputs = WorkflowInputs()
      inputs = inputs.update(polInput)
      inputs = inputs.update(porInput)

      val json = inputs.toJson
      val deserialized = json.convertTo[WorkflowInputs]

      deserialized.inputs.size shouldBe 2
      deserialized.get("pol").map(_.stepId) shouldBe Some("pol")
      deserialized.get("por").map(_.stepId) shouldBe Some("por")
    }
  }

  "StepInputReq" should {

    "serialize and deserialize correctly" in {
      val req = StepInputReq(
        stepId = "pol",
        data = JsObject(
          "liabilities" -> JsArray(
            JsObject("userId" -> JsString("user1"), "amount" -> JsNumber(1000))
          )
        )
      )

      val json = req.toJson
      val deserialized = json.convertTo[StepInputReq]

      deserialized.stepId shouldBe "pol"
      deserialized.data shouldBe a[JsObject]
    }

    "convert to StepInput" in {
      val req = StepInputReq(
        stepId = "pol",
        data = JsObject("value" -> JsNumber(123))
      )

      val stepInput = StepInput(
        stepId = req.stepId,
        data = req.data
      )

      stepInput.stepId shouldBe "pol"
      stepInput.data shouldBe req.data
    }
  }

  "StepInputRes" should {

    "serialize and deserialize correctly" in {
      val res = StepInputRes(
        success = true,
        message = "Step input updated successfully"
      )

      val json = res.toJson
      val deserialized = json.convertTo[StepInputRes]

      deserialized.success shouldBe true
      deserialized.message shouldBe "Step input updated successfully"
    }

    "handle error responses" in {
      val res = StepInputRes(
        success = false,
        message = "Failed to update step input: Workflow not found"
      )

      res.success shouldBe false
      res.message should include("Workflow not found")
    }
  }

  "Step Input API JSON compatibility" should {

    "parse valid API request" in {
      val jsonStr = """
        {
          "stepId": "pol",
          "data": {
            "liabilities": [
              {"userId": "user1", "amount": 1000},
              {"userId": "user2", "amount": 2000}
            ]
          }
        }
      """

      val req = jsonStr.parseJson.convertTo[StepInputReq]

      req.stepId shouldBe "pol"
      req.data shouldBe a[JsObject]
    }

    "format API response" in {
      val res = StepInputRes(success = true, message = "Signal delivered")
      val jsonStr = res.toJson.prettyPrint

      jsonStr should include("\"success\": true")
      jsonStr should include("\"message\": \"Signal delivered\"")
    }
  }
}
