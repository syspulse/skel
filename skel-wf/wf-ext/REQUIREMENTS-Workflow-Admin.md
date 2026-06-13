# Workflow Admin UI

Workflow Framework is described in [REQUIREMENTS-Workflow.md](REQUIREMENTS-Workflow.md)

Admin UI allows to Manage WorkflowGraf visually from UI:

- CRUD on WorfklowSchema 
- CRUD on WorkflowConfig from WorkflowSchema
- Visually edit WorkflowGraf topology in UI Editor
- UI Details for viewing DetectorSchema properties
- UI Details for viewing and editing DetectorConfig properties
- UI Details for editing WorkflowNode and WorkflowLink properties


## skel-admin

- Workflow Admin UI must be in [skel-admin](../../skel-fe/skel-admin) as a Dedicated `Workflow` Module
- Workflow Admin UI must re-use components, libraries in __skel-admin__ and avoid implementing from scratch if already exists
- Workflow Module Menu panel must support submenus. Each submenu is an instance of WorkflowSchema or WorkflowConfig. 
  It must have a corresponding tag: (`config`,`schema`) after the name
  Submenu title is the name of the WorkflowSchema or WorkflowConfig instances.
- Main Menu "Workflow" must have Tabs: "Schema", "Config", "DetectorSchema", "DetectorConfig"
- Each Tab must have its own Table with corresponding entities. Table must be implemented the same UI/UX style and behavior as  "Explain" module
- Clicking on WorkflowSchena instance in "Schema" Tab or WorkflowConfig "Config" tab must open details with corresponding properties. Deails View must be implemented very similar to "Explain" Details UI/UX style and behavior
- On the Top of the Details Panel, there should be a button [Edit] to navigate to corresponding object's WorkflowGraf View
- All objects (WorkflowSchema,WorkflowConfig,DetectorSchema,DetectorConfig) must support [Add] and [Delete] in Table view

## WorkflowGraf View

WorkflowGraf view must allow to visually edit the topology graph:

- Use [react-flow] Library for Graph editor
- Use the same `id` from WorkflowNode,WorkflowLink for react-flow components if possible
- Desing it as re-usable Worfklow Editor Component
- Workflow Editor Component must have its own 2 panels 
  Panel 1 is used for showing the Workflow Icon, Title, Name, type (`config`,`schema`)
  Panel 2 is has [Add],[Del],[Clear],[Save] buttons, Search field (Nodes titles quick search)
- Editor must support Adding Node (DetectorConfig or DetectorSchema). Available nodes: DetectorConig and DetectorSchema objects 
  Default attributes "title","icon","tags" are copied during creation from corresponding objects to WorkflowNode
- It must be possible to link one Node to another Node with WorkflowLink.
  Every node must have a visible input and output __connection__ (square by default, on all side of the node) is used for connecting nodes via react-flow "edge". Edge Arrow must indicate "to" destination connection.
- Map WorkflowNode to react-flow Node
- Map WorkflowLink to react-flow Edge with a direction
- WofklowLink "from" and "to" define the direction and arrow.
- Since WorkflowNode might have mulitple WorkflowLinks (to and from), react-flow must support mulitple Edges and Connections.
- react-flow Edge must support edge name label
- react-flow Node must show title from the corresponding object (DetectorSchema or DetectorConfig) title attribute
- react-flow Node must show optional icon from the WorkflowNode 
- react-flow Node must use the well defined styles for visualization and ability to customize style from WorklfowNode and WorkflowLink `meta` attributes
- Selecting the Node or Connection must open sliding Details Panel with attributes of the corresponing WorkflowNode or WorkflowLink with editing capabilities. Same UI/UX style and behavior as "Explain" details
- It should be possible to edit the icon of the Node not only by URL, but also a small panel of quickly selectable icon images availble in the Admin (3 lines max)
- It should be possible to edit the edge, name and background colors of the selected Node or Link in the Details.
  Corresponding object `meta` should be used for persisting and using this attribute during initial loading
- It should be possible to resize Node vertically and horizontally
- The topology (positions, size) must be persisted in meta attributes and used during initial loading
- [Save] button should be used to persist topology and visual modifications to WorkflowStore.




