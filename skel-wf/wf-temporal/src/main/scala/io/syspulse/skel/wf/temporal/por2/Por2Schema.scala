package io.syspulse.skel.wf.temporal.por2

import io.hacken.ext.wf.{WorkflowSchema, WorkflowSchemaNode, WorkflowSchemaConnection}
import io.hacken.ext.detector.{DetectorConfig, DetectorConfigContract, DetectorSchema}
import spray.json._

/**
 * PoR Workflow Schema Builder for Generic Workflow Framework
 *
 * Creates a WorkflowSchema that defines the PoR flow:
 * PoO → PoR → PoL → Solvency → Report → Commit
 */
object Por2Schema {

  /**
   * Build DetectorSchema instances for each step
   */
  private def buildDetectorSchemas(tenantId: Int, projectId: Int): Seq[DetectorSchema] = {
    val ts = System.currentTimeMillis()

    Seq(
      DetectorSchema(
        id = 1,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "ProofOfOwnership",
        version = "1.0.0",
        title = "Proof of Ownership",
        description = "Verify ownership of wallets",
        author = "Syspulse",
        icon = Some("key"),
        faq = None,
        tags = Seq("poo", "ownership"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      ),
      DetectorSchema(
        id = 2,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "ProofOfReserve",
        version = "1.0.0",
        title = "Proof of Reserve",
        description = "Calculate total reserves",
        author = "Syspulse",
        icon = Some("coins"),
        faq = None,
        tags = Seq("por", "reserves"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      ),
      DetectorSchema(
        id = 3,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "ProofOfLiability",
        version = "1.0.0",
        title = "Proof of Liability",
        description = "Verify total liabilities",
        author = "Syspulse",
        icon = Some("file-text"),
        faq = None,
        tags = Seq("pol", "liabilities"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      ),
      DetectorSchema(
        id = 4,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "Solvency",
        version = "1.0.0",
        title = "Solvency Calculation",
        description = "Calculate solvency ratio",
        author = "Syspulse",
        icon = Some("calculator"),
        faq = None,
        tags = Seq("solvency", "ratio"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      ),
      DetectorSchema(
        id = 5,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "Report",
        version = "1.0.0",
        title = "Report Generation",
        description = "Generate audit report",
        author = "Syspulse",
        icon = Some("file-pdf"),
        faq = None,
        tags = Seq("report", "pdf"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      ),
      DetectorSchema(
        id = 6,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        name = "Commit",
        version = "1.0.0",
        title = "Commit Results",
        description = "Save results to storage",
        author = "Syspulse",
        icon = Some("database"),
        faq = None,
        tags = Seq("commit", "storage"),
        networkTags = Seq(),
        schema = None,
        uiSchema = None
      )
    )
  }

  /**
   * Build a complete PoR workflow schema
   *
   * @param schemaId Schema ID
   * @param tenantId Tenant ID
   * @param projectId Project ID
   * @return WorkflowSchema for PoR flow
   */
  def buildSchema(
    schemaId: Int = 1,
    tenantId: Int = 1,
    projectId: Int = 1,
    title: String = "Proof of Reserve Workflow"
  ): WorkflowSchema = {

    val ts = System.currentTimeMillis()
    val detectorSchemas = buildDetectorSchemas(tenantId, projectId)

    // Define nodes (steps) in the workflow
    val nodes = detectorSchemas.map { schema =>
      WorkflowSchemaNode(
        id = schema.id,
        name = schema.title,
        sid = schema.id,
        typ = Some("detector"),
        icon = schema.icon,
        schema = schema
      )
    }

    // Define connections (edges) between nodes
    val connections = Seq(
      WorkflowSchemaConnection(id = 1, from = 1, to = 2),  // PoO → PoR
      WorkflowSchemaConnection(id = 2, from = 2, to = 3),  // PoR → PoL
      WorkflowSchemaConnection(id = 3, from = 3, to = 4),  // PoL → Solvency
      WorkflowSchemaConnection(id = 4, from = 4, to = 5),  // Solvency → Report
      WorkflowSchemaConnection(id = 5, from = 5, to = 6)   // Report → Commit
    )

    WorkflowSchema(
      id = schemaId,
      createdAt = ts,
      updatedAt = ts,
      status = "ACTIVE",
      name = "PoR-Flow",
      version = "2.0.0",
      title = title,
      description = "Complete PoR workflow: PoO → PoR → PoL → Solvency → Report → Commit",
      author = "Syspulse",
      icon = Some("shield-check"),
      faq = None,
      tags = Seq("por", "audit", "reserves"),
      nodes = nodes,
      connections = connections
    )
  }

  /**
   * Get step IDs for a specific flow type
   *
   * @param flow Flow name (flow-1 through flow-5)
   * @return Sequence of step IDs to include in the flow
   */
  def getFlowSteps(flow: String): Seq[Int] = {
    flow match {
      case "flow-1" => Seq(1, 2, 3, 4, 5, 6)  // PoO -> PoR -> PoL -> Solvency -> Report -> Commit
      case "flow-2" => Seq(2, 3, 4, 5, 6)     // PoR -> PoL -> Solvency -> Report -> Commit
      case "flow-3" => Seq(2, 5, 6)           // PoR -> Report -> Commit
      case "flow-4" => Seq(1, 2, 5, 6)        // PoO -> PoR -> Report -> Commit
      case "flow-5" => Seq(3)                 // PoL only
      case _ => throw new IllegalArgumentException(s"Unknown flow: $flow. Valid flows: flow-1, flow-2, flow-3, flow-4, flow-5")
    }
  }

  /**
   * Build DetectorConfig instances for each step
   *
   * @param tenantId Tenant ID
   * @param projectId Project ID
   * @return Seq of DetectorConfig (one per step)
   */
  def buildStepConfigs(
    tenantId: Int = 1,
    projectId: Int = 1
  ): Seq[DetectorConfig] = {

    val ts = System.currentTimeMillis()

    // Helper to create a contract
    def createContract(id: Int, name: String) = DetectorConfigContract(
      id = id,
      createdAt = ts,
      updatedAt = ts,
      projectId = projectId,
      tenantId = tenantId,
      chainUid = None,
      proxyAddress = None,
      implementation = None,
      address = None,
      name = name
    )

    Seq(
      // Step 1: PoO
      DetectorConfig(
        id = 1,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(1, "PoO-Contract"),
        schema = None,
        name = "ProofOfOwnership",
        source = "POR2",
        tags = Seq("poo", "ownership"),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "proofType" -> JsString("signature")
        )),
        destinations = Seq()
      ),

      // Step 2: PoR
      DetectorConfig(
        id = 2,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(2, "PoR-Contract"),
        schema = None,
        name = "ProofOfReserve",
        source = "POR2",
        tags = Seq("por", "reserves"),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "assets" -> JsArray(
            JsString("ETH"),
            JsString("BTC"),
            JsString("USDT")
          )
        )),
        destinations = Seq()
      ),

      // Step 3: PoL
      DetectorConfig(
        id = 3,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(3, "PoL-Contract"),
        schema = None,
        name = "ProofOfLiability",
        source = "POR2",
        tags = Seq("pol", "liabilities"),
        config = Some(JsObject(
          "type" -> JsString("WAIT"),  // Requires manual confirmation
          "waitForConfirmation" -> JsBoolean(true),
          "signalMode" -> JsString("simulate")
        )),
        destinations = Seq()
      ),

      // Step 4: Solvency
      DetectorConfig(
        id = 4,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(4, "Solvency-Contract"),
        schema = None,
        name = "Solvency",
        source = "POR2",
        tags = Seq("solvency", "ratio"),
        config = Some(JsObject(
          "type" -> JsString("AUTO")
        )),
        destinations = Seq()
      ),

      // Step 5: Report
      DetectorConfig(
        id = 5,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(5, "Report-Contract"),
        schema = None,
        name = "Report",
        source = "POR2",
        tags = Seq("report", "pdf"),
        config = Some(JsObject(
          "type" -> JsString("AUTO"),
          "enabled" -> JsBoolean(true)
        )),
        destinations = Seq()
      ),

      // Step 6: Commit
      DetectorConfig(
        id = 6,
        createdAt = ts,
        updatedAt = ts,
        status = "ACTIVE",
        contract = createContract(6, "Commit-Contract"),
        schema = None,
        name = "Commit",
        source = "POR2",
        tags = Seq("commit", "storage"),
        config = Some(JsObject(
          "type" -> JsString("AUTO")
        )),
        destinations = Seq()
      )
    )
  }
}
