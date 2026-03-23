package io.syspulse.skel.wf.temporal.workflow.store

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.wf.temporal.por2.Por2Schema
import io.syspulse.skel.wf.temporal.demo.DemoSchema

/**
 * Test store pre-populated with all configs from Por2 and Demo
 *
 * This store inherits from WorkflowConfigStoreMem and automatically
 * initializes with all DetectorConfig instances for testing and development.
 *
 * Pre-populated configs:
 * - Por2: IDs 100-105 (ProofOfOwnership, ProofOfReserve, ProofOfLiability, Solvency, Report, Commit)
 * - Demo: IDs 200-202 (StepAuto, StepHuman, StepAuto for 3-step flow)
 */
class WorkflowConfigStoreTest extends WorkflowConfigStoreMem {
  private val log = Logger(getClass)

  // Pre-populate with all Por2 configs
  val por2Configs = Por2Schema.buildStepConfigs(tenantId = 1, projectId = 1)
  por2Configs.foreach { config =>
    this.+(config)
    log.info(s"Pre-populated Por2 config: ${config.name} (id=${config.id})")
  }

  // Pre-populate with Demo configs (3-step flow as example)
  val demoConfigs = DemoSchema.buildStepConfigs(Seq("auto", "human", "auto"))
  demoConfigs.foreach { config =>
    this.+(config)
    log.info(s"Pre-populated Demo config: ${config.name} (id=${config.id})")
  }

  log.info(s"WorkflowConfigStoreTest initialized with ${this.all.size} configs")
  log.info(s"  Por2 configs: ${por2Configs.map(c => s"${c.id}:${c.name}").mkString(", ")}")
  log.info(s"  Demo configs: ${demoConfigs.map(c => s"${c.id}:${c.name}").mkString(", ")}")
}
