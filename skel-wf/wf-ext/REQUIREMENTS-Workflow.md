# Workflow Framework with DetectorConfig and DetectorSchema entities

Existing __Ext__ Detector framework (Setinel) is desinged for One-Step streaming continueous workflows.

It has 1 single manual user configuration step and after it runs (state=ACTIVE) (runs usually usually indefinitely) until stopped (state=DISABLED).

This framework uses two core entities:

- DetectorConig [DetectorConfig.scala]
- DetectorSchema [DetectorSchema.scala]


## DetectorSchema

DetectorSchema defines configuration attributes, type and description for Detector configuration options.
Schema is used by UI to render attributes for user modifications.
It is important to note, that DetectorSchema attributes have "default" values. 
Thus DetectorSchema is used as a pre-configured template for DetectorConfig runtime configuration. 
It allows to build mulitple pre-set configuration `templates` of the same Detector implementation

## DetectorConfig

DetectorConfig is runtime Detector instance with configuraiton attributes created during Detector instance creation.
DetectorConfig contains "schema" copied  from DetectorSchema for which DetectorCondig's instance is created.
The reason to use full "schema" object instead of just "schema_id" is to be able to work with config attributes even if
corresponding DetectorSchema object was deleted.
Thus DetectorConfig can be treated as instance of DetectorSchema.
Since any number of Detectors can be created, the realtionship is one-to-many:
1 DetectorSchema -> * DetectorConfig

## Goal

The primary goal is to design and implement Workflow Framework and Workflow Service which uses existing DetectorConfig/DetectorSchema to 
manage Workflow template (WorkflowConfig/WorkflowSchema) which allows to link DetectorConfigs into a DAG graph (Direct Acyclic Graph).
Runtime of the DAG is not a goal for this Requirement
It must be ready to use in UI `skel-admin` "Workflow" module in the future


### WorkflowSchema

WorkflowSchema defines the template for Workflow DAG view similar way DetectoSchema defines the template for individual Node of the DAG.
Graph node is represented by DetectorSchema.

WorfkflowSchema has no workflow functional meaning, it is only used for graoh mapping to the real umplementaiton logic which are separate concerns. 
It is a responsibility of the implementation to design flow of the graph. 
WorkflowSchema may not even define some steps of the workflow graph


## WorkflowConfig

WorkflowConfig is effectively and instance of the WorkflowSchema which defines instance config data.
WorkflowConfig is always created from WorkflowSchema
Config data is directly copied from corresponding WorkflowSchema default and can modified by the user at any time
With this approach, the Runtime Workflow engine can read and write into WorkflowConfig objects during runtime.
Effectitely WorkflowConfig becomes the runtime state of the Workflow runtime instance.
Real workflow engine implemetnation can use its own state management. Storing state in the WorkflowConfig is not a mandatory
feature of the `ext` Workflow framework, same as with DetectorConfig concept (it is a config runtime store for running Detectors and not state store of the running detector instance)


### WorkflowGraf

WorkflowGraf is a visual representation of the WorkflowSchema and WorkflowConfig with visual attributes (connection, icon, name..)
It can be both "template" and "runtime instance":

- If WorkflowGraf.cid is None, it is "template"
- If WorkflowGraf.cid is Some(id), it is "runtime instance"

- WorkflowGraf visually represents Workflow at template stage (WorkflowSchema) and runtime stage (WorkflowConfig)
- In template mode, the WorkflowGrap is a template with default nodes, connections, attributes, visual topology
  There are no DetectorConfig instances, thus WorkflowGraf cannot be linked to runtime
  At this stage the WorkflowGrap has a direct link to unique instance of WorkflowConfig with runtime instances of DetectorConfig nodes.
  Since user may edit any Graph during creation and runtime, WorkflowGraf is also an instance of the runtime

WorkflowGraf supports many-to-many connections to the same WorkflowNode
Because of this, WorkflowNode contains the Map of WorkflowLink-s to navigate from this node back and forth (e.g. `cursor` concept)
For quick navigation from WorkflowGraf, WorkflowGraf has Maps of all WorkflowNode and WorkflowLinks
For this reason, Framework must keep in sync WorkflowNodes.links


#### WorkflowNode 

WorkflowNode visually represents WorkflowSchema or WorkflowConfig

`meta` fiels contain visual information for rendering, like position, size, colors or style

- __style__ attribute must directly map to CSS styles defined by visual rendering engine (`react-flow`)
- `pos` - defines position of the Node in Rendering view
- `size` - defines size of the Node

Any visual attributes defined in `meta` must override `style` attributes if present

#### WorkflowLink

WorkflowLink visually represents connections between WorkflowNodes.

----

### Core Features

- `mem://` and `dir://` Datastore for Workflow's objects. 
  Single WorkflowStore. not mulitple stores. Refactor existing file to WorkflowStore
- WorkflowStore must be only Async (Future). Consult $SKEL_HOME/skel-user how UserStore is defined and implemented
- WorkflowStore must support paging (see UserStore for paging)
- WorkflowStore must support findByOid and findByXid (see UserStore for reference)
- CRUD for all entities (WorkflowSchema, WorkflowConfig, WorkflowGraf)
- Comprehensive Tests Suite for WorkflowSchema and WorkflowConfig. This is the primary API which will be used to work with Workflows
- Comprehensive Tests Suite for WorkflowGraf with different topologies (Node, Links, positions and sizes)
- App must support "schema" command to create WorkflowSchema from DSL language
- App must support "assembly" command to create WorkflowConfig from DSL (which will create WorkflowsSchema underneath)



#### Assembly DSL 

Assembly allows to quickly build Workflows.
It must bea be able to create and link:

- New WorkflowSchema with WorkflowGraf and WorkflowNodes/WorkfloLinks connected from new DetectoSchema objects
- New WorkflowConfig with underlying WorkflowSchema and WorkflowGraf and WorkflowNodes/WorkfloLinks connected from new DetectorSchema and DetectorConfig objects
- New WorkflowSchema with WorkflowGraf and WorkflowNodes/WorkfloLinks connected from existing DetectoSchema objects (reference by `id`)
- New WorkflowConfig with underlying WorkflowSchema and WorkflowGraf and WorkflowNodes/WorkfloLinks connected from new DetectorSchema and DetectorConfig objects from existing DetectoConfig objects (referenced by `id`)

Syntax: "{in}{entity}.{name|id}.{out} ->"

1. {in}  - optional input DetectorLink.id. If omitted, then first id form links Map is used
2. {entity} - "Schema" or "Detector" specified if output should be DetectorSchema ("Schema") only or DetectorConfig ("Detector")
3. {name|id} - name or id of the entity. If `id` is specidied, then datastore must be looked up for corresponding entity ID (DetetcorSchema or DetectorConfig IDs).
4. {out} - optional output DetectorLink.id. If omitted, then first id form links Map is used
5 -> - optional DetectoLink to the next DetectorNode.

```
{Detector.name1} -> {Detector.name2.0} -> {1.Detector.name3.1}
```

This pipeline will create:

- 3 DetectorSchema with names: "Schema_name1","Schema_name2","Schema_name3"
- 3 DetectorConfigs from these Schemas with names: "name1,"name2","name3"
- 1 WorkflowSchema with id (`--wid=`) and name (`--wn=`) or random name. Default Id should start with 0 and never be negative
- 1 WorkflowConfig from created WorkflowSchema and corresponding linked DetectorConfigs
- Lined WorkflowNodes with WorkflowLinks


### Extra Features

- wf-schema-get, wf-schema-create.sh, wf-schema-update, wf-schema-del Shell scripts (see user-*.sh for reference)
- wf-config-get, wf-config-create.sh, wf-config-update, wf-config-del Shell scripts (see user-*.sh for reference)


## API

[/api/v1/wf/ext/schema] - WorkflowSchema API. 

[/api/v1/wf/ext/config] - WorkflowConfig API

[/api/v1/wf/ext/graf] - WorkflowGraf API to be used in UI during visual configuration of the Workflow

`schema` and `config` APIs must support `?detector={id|full}` option to retrieve either just detector/schema id or full DetectorConfig/DetectorSchema objects. Default is `id`.

