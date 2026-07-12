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

### WorkflowConfig Temporal Engine Mapping:

[Workflow](doc/temporal-wf-1.png)

https://temporal.dev.hacken.cloud/namespaces/dd_reports/workflows/dd-report-falcon-05291146-fbf9/83540814-8328-4fef-aad1-23d2d577f9e5/history


`WorkflowConfig.xid` => Temporal RunID ( `83540814-8328-4fef-aad1-23d2d577f9e5` )
`WofkflowConfig.name` => Temporal Workflow Name ( `DDReportWorkflow` )


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
```

#### Temporal Workflow Activities -> WorkflowSchema/WorkflowConfig mapping 

WorkflowSchema/WorkflowConfig describes a partial representaton of the Temportal Workflow Activities
It is important to highlight, that it does NOT represent 1:1 all Temporal Activities, but only what WorkflowSchema deems important
For example Temporal Workflow may have 10 activities but WorkflowSchema wants to show only 3 most important steps (Activity)
Missing Acitivities in WorkflowSchema are not visible.

Mapping of Temporal Worklfow Acitivity/Child Workflow is matched by `Workflow Type Name` or `Activity Type`


## DetectorSchema

DetectorSchema is not used in Runtime.

## DetectorConfig

DetectorConfig is runtime Detector instance with configuraiton attributes and "binding" to some
Activity or Child-Workflow in the Workflow runtime instance.

Correlation resolution is done via one of the options in this order from DetectorConfig:

1. Temporal Acitivity Type == `DetectorConfig.name` 
2. Temporal Child Workflow Type Name == `DetectorConfig.name` 
3. `DetectorConfig.meta["wid"]` (dd-report-aave-07092333-52bd.source-comparison)
4. `DetectorConfig.meta["cid"]`


Activity implementation must publish `cid` (DetectorConfig.id) to the Workflow Engine state. 
This allows to pull the state from Workflow and visualize current state in WorkflowGraf without limitation of Temporal identifiers


## Goal

The primary goal is to design and implement Workflow Engine abstraction layer which would allow to 
map Engine runtime Workflow and Activities state to runtime WorkflowConfig

- Poll current state from Engine
- Map Engine's runtime instance state to WorkflowConfig status (e.g. COMPLETED, RUNNING, PAUSED, FAILED, TERMINATED, ...)
- Map Engine's current Activity instance state to DetectorConfig state (e.g. COMPLETED, RUNNING, FAILED, TERMINATED)
- Map instance of WorkflowConfig to existing Engine Workflow Runtime by `xid`


----

### Core Features

- Engine must be abstracted
- `Temporal` Engine must be implemented as a primary supported Engine.
- Use [wf-temporal](../wf-temporal) for guidelnes and implementation for Temporal Access
 
- Command to link WorkflowConfig created from DSL to Temporal via `xid`.
  Example (create Proof of Reserve WorkflowConfig flow instance with 3 DetectroConfig and link to Temporal Runtime with RuntimeID=019e7473-05d1-789f-bb4b-44845bd69fc6):
  ```
  ./run-wf.sh --engine=temporal:// assembly-link 019e7473-05d1-789f-bb4b-44845bd69fc6  '[PoO] -> [PoR] -> [Report]'
  ``` 

- Command to get all Runtime workflows.
  Example:
  ```
  ./run-wf.sh --engine=temporal:// runtime-get'
  ``` 

- Command to get Runtime workflow.
  Example:
  ```
  ./run-wf.sh --engine=temporal:// runtime-get 019e7473-062b-7e26-8925-e202fe0285a1'
  ``` 



## API

[/api/v1/wf/ext/engine/{engine}/] - WorkflowConfig API

{engine} is what `wf-ext` supports:

1. temporal ([/api/v1/wf/ext/engine/temporal/])

Engine API must support:

- Get Workflow runtime state by `runtimeId`

[/api/v1/wf/ext/engine/temporal/{namespace}] - get all Workflows in runtime in specific `namespace` (e.g. namespace=default)
[/api/v1/wf/ext/engine/temporal] - get all Workflows in runtime in all namespaces
[/api/v1/wf/ext/engine/temporal/{namespace}/{runtime_id}] - get Workflow by runtime_id

Exmaple:
[/api/v1/wf/ext/engine/temporal/{namespace}/workflows/dd-report-aave-dao-07100911-dad1/9424bad9-02c3-40cd-9031-ec231fda8017]



