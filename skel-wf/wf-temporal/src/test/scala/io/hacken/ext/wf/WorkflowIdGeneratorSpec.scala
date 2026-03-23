package io.hacken.ext.wf

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class WorkflowIdGeneratorSpec extends AnyWordSpec with Matchers {

  "WorkflowIdGenerator" should {

    "use title as template when it contains placeholders" in {
      val schema = WorkflowSchema(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        name = "PoR-Flow",
        version = "1.0.0",
        title = "{name}-{project}-{tid}-{ts}",  // Contains placeholders
        description = "Test workflow",
        author = "Test",
        icon = None,
        faq = None,
        tags = Seq.empty,
        nodes = Seq.empty,
        connections = Seq.empty
      )

      val context = Map(
        "tid" -> "123",
        "project" -> "TestProject"
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context
      )

      // Should use title as template and replace all placeholders
      workflowId should startWith("PoR-Flow-TestProject-123-")
      workflowId should not contain "{"
      workflowId should not contain "}"
    }

    "use title as-is when title has no placeholders" in {
      val schema = WorkflowSchema(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        name = "PoR-Flow",
        version = "1.0.0",
        title = "Proof of Reserve Workflow",  // No placeholders - used as-is
        description = "Test workflow",
        author = "Test",
        icon = None,
        faq = None,
        tags = Seq.empty,
        nodes = Seq.empty,
        connections = Seq.empty
      )

      val context = Map(
        "project" -> "TestProject"
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context,
        defaultTemplate = "por2-{project}-{ts}"  // Ignored - not used anymore
      )

      // Should use title as-is (no placeholders to replace)
      workflowId shouldEqual "Proof of Reserve Workflow"
    }

    "replace all standard placeholders correctly" in {
      val schema = WorkflowSchema(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        name = "TestWorkflow",
        version = "1.0.0",
        title = "{name}-{title}-{tid}-{pid}-{project}-{ts}",
        description = "Test workflow",
        author = "Test",
        icon = None,
        faq = None,
        tags = Seq.empty,
        nodes = Seq.empty,
        connections = Seq.empty
      )

      val context = Map(
        "tid" -> "T123",
        "pid" -> "P456",
        "project" -> "MyProject"
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = context
      )

      // Verify all placeholders were replaced
      workflowId should include("TestWorkflow")  // {name}
      workflowId should include("T123")          // {tid}
      workflowId should include("P456")          // {pid}
      workflowId should include("MyProject")     // {project}
      workflowId should not contain "{"
      workflowId should not contain "}"
    }

    "handle empty context gracefully" in {
      val schema = WorkflowSchema(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        name = "SimpleWorkflow",
        version = "1.0.0",
        title = "{name}-{ts}",
        description = "Test workflow",
        author = "Test",
        icon = None,
        faq = None,
        tags = Seq.empty,
        nodes = Seq.empty,
        connections = Seq.empty
      )

      val workflowId = WorkflowIdGenerator.generateFromSchema(
        schema = schema,
        context = Map.empty
      )

      // Should work with just schema-provided values
      workflowId should startWith("SimpleWorkflow-")
      workflowId should not contain "{"
      workflowId should not contain "}"
    }

    "generate unique IDs due to timestamp" in {
      val schema = WorkflowSchema(
        id = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        status = "ACTIVE",
        name = "TestWorkflow",
        version = "1.0.0",
        title = "{name}-{ts}",
        description = "Test workflow",
        author = "Test",
        icon = None,
        faq = None,
        tags = Seq.empty,
        nodes = Seq.empty,
        connections = Seq.empty
      )

      val id1 = WorkflowIdGenerator.generateFromSchema(schema)
      Thread.sleep(10)  // Ensure different timestamp
      val id2 = WorkflowIdGenerator.generateFromSchema(schema)

      id1 should not equal id2
    }
  }
}
