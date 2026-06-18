# Workflow Engine

Workflow `Engine` is a binding of the Workflow Runtime Engine with wf-ext.

Wrokflow Engine can be any Orchestration Runtime Engine/Framework which actually runs Workflow Activities or Steps,
while wf-ext only provides configuration of individual activties (steps) and visualization of the flow.
Runtime state and orchestration is always on the Engine side.

Activity (Step) is represtned as DetectorConfig instance in wf-ext.
Activity underlying implementation examples:

- AI Agent
- Human workflow (with human confirmation steps)

## Engine

Supported Engine:

1. [temporal](https://temporal.io)

Engine has two ID concepts:

1. workflowId  - ID of the Workflow definition. It is correlated to WorkflowSchema.
2. runtimeId - ID of the runtime instance of the Workflow and 


## WorkflowSchema

WorkflowSchema is not used in Runtime.

`WorkflowSchema.id` is internal id of the workflow and not linked to Engine `workflowId`.

## WorkflowConfig

WorkflowConfig is a representation of runtime Workflow instance in the Engine 

- `WorkflowConfig.id` is internal id of the workflow and not linked to Engine `runtimeId`.
- `WorkflowConfig.xid` can be linked to Engine `runtimeId`. It is a __strict__ requirement.

### WorkflowConfig Temporal Example:

[Workflow](doc/temporal-wf-1.png)

https://temporal.dev.hacken.cloud/namespaces/dd_reports/workflows/dd-report-falcon-05291146-fbf9/83540814-8328-4fef-aad1-23d2d577f9e5/history

Temporal WorkflowID: `dd-report-falcon-05291146-fbf9` => `WorkflowConfig.id`
Temporal RunID: `83540814-8328-4fef-aad1-23d2d577f9e5` => `WorkflowConfig.xid`

Because Temporal Workflow usually has Child Subworkflows, they all share the same Workflow ID prefix,
but different RunID:


[Child Workflow](doc/temporal-wf-2.png)

```
Workflow Type Name: ForkAuditDiscoveryWorkflow
Workflow ID: dd-report-falcon-05291146-fbf9.fork-audit-discovery
Run ID: 019e738f-8f38-7b12-9e07-d73844d65477
```

Temporal Engine must correctly match all Child workflows for `xid`:

```
dd-report-falcon-05291146-fbf9.fork-audit-discovery / 019e738f-8f38-7b12-9e07-d73844d65477
dd-report-falcon-05291146-fbf9.whitehat / 019e7473-05d1-789f-bb4b-44845bd69fc6
dd-report-falcon-05291146-fbf9.opsec / 019e7473-062b-7e26-8925-e202fe0285a1

...

```


## DetectorSchema

DetectorSchema is not used in Runtime.

## DetectorConfig

DetectorConfig is runtime Detector instance with configuraiton attributes and "binding" to some
Activity in the Workflow runtime instance.

Correlation resolution is done via one of the options in this order:

1. `DetectorConfig.id`
2. `DetectorConfig.name` 

If option resolution fails, Workflow Resolver moves to the next 
`DetectorConfig.name` is 

Activity implementation must publish `cid` (DetectorConfig.id) 
to the Workflow Engine state. This allows to pull the state from Workflow and visualize current sate in WorkflowGraf.


## Goal

The primary goal is to design and implement Workflow Engine abstraction layer which would allow to 
map Engine runtime Workflow and Activities state to wf-ext:

- Poll current state from Engine
- Map Engine's runtime instance state to WorkflowConfig status (e.g. RUNNING, PAUSED, FAILED, DISABLED, ...)
- Map Engine's current Activity instance state to DetectorConfig state (e.g. ACTIVE,DISABLED,FAILED)

## WorkflowGraf


----

### Core Features

- Engine must be abstracted
- Temporal Engine must be implemented as a primary supported Engine.
  Use [wf-temporal](../wf-temporal) for guidelnes and implementation
- 


## API

[/api/v1/wf/ext/engine/{engine}] - WorkflowConfig API

{engine} is what `wf-ext` supports:

1. temporal

Engine API must support:

- Get Workflow runtime state by `runtimeId`
- Get Workflow activity state by `runtimeId` and custom fields in state e.g. (`cursor`)


