

# Event Plane Internal Dispatcher

The purpose of the internal dispatcher is to handle and route events (commands) to the appropriate service/module 
and update screens (UI) not only by user inputs/actions, but by events (commands) executed externally or internally not by user

Events may come from different sources:

- Cron
- WebSocket (Notifications)
- WebSocket (MCP message)
- SSE or Websocket Telemetry
- SSE or WebSocket Data Stream
- Internal Command

Prmary use-cases of the Dispatcher: 

### Websocket Notifications

`Notify Backend Service` delivers live Notification to All connected Clients (e.g. "New Release")

### Websocket MCP Integration

MCP Server delivers event (command) to FrontEnd to perform the action typically performed by User on a screen and update UI asynchronously
For example, new User can be added in User 

Dispatcher must support:

- Fully asynchronous Events processing
- Support queue for Events if incoming events/commands are fast (e.g. from Websocket)
- Drop events on queue overflow and write to the console about logs


## Dispatcher Websocket

- Websocket client must support connecting to remote Websocket Service for Events
- Websocket client must support optional Authentication (JWT Bearer)
- Websocket client configuration default must be in `.env` 
- Websocket client must support Chrome, Brave, Firefox, Apple and Microsoft browsers


## Dispatcher __Event__ format

id: String         - uniq Event id
ts: Long           - timestamp of the event
auth: Option[String] - optional authorization
sev:Option[Double] - optional severity (range: [0..1.0]). 
src:Option[String] - source ID of the Event ("local","api","angent-1","mcp","telemetry")
dst:Option[String] - destination ID of the event ("client-1","product"). Products can differentiate if it is specfically targted
                     For example, "Release Update" is delivered to only to "client-1" and not globally.
sys:String         - Service (System) which should process the Event (e.g. "","NotificationSys","ExplainSys","SyslogSys").
typ:Option[String] - Type of event ("COMMAND","ALERT","NOTIFY","SYSLOG","DATA")
cmd:Option[String] - event command to execute ("Notify","User","Update","Log")
data:JsObject      - data associated with Event (flexible Json object). `sys` knows how to parse the data

### Event Authorization

Optional `auth` field may contain:

1. `Authentication: Bearer <jwt>`
2. `Sig: <Algo> <signature>`

It present, Dispatcher must verify it with public key. This functionality is reserved for the future

### Event Severity

Default Severity mapping
```
object Severity {
  
  val CRITICAL = 0.75
  val HIGH = 0.5
  val MEDIUM = 0.25
  val LOW = 0.15
  val INFO = 0.1
  val NONE = 0.0
  val AUTO = -1.0
  val ERROR = -0.5  
}
```

Severity Mapping is custom per System, but unification is desired.


## Core Supported Functionality

1. Internal src="local" Notifications (cmd=NOTIFY) -> sys="NotificationSys"
2. External src="notify" Notifcation (cmd=NOTIFY) via Websocket -> sys="NotificationSys"
3. External src="api" API Call to add / update / delete entity (Explain) on Backend via Websocket -> sys="ExplainSys" to update Explain table
4. NotificationSys must always register itself with Dispatcher with `sys`="NoficationSys" and `sys`="". 
   When `sys` is not specified or blank, it should be routed to NoficiationSys
5. Internal ("local") notifications must go through Dispatcher.


## Implementation Guidelines

- Keep functionality in a dedicated module/component
- Reuse `Event` object fields as much as possible (e.g. Notification)
- Severity string can be custom per System, E.g. NotificationSys will have its own mapping (e.g. `0.0`  = Success, `0.1` = Info, `0.3` = Warning, `0.5` = Error)
- Keep all 3 core Functionality use-cases supported
- Support extending new "src", "dst", "typ", "cmd" processing
- Each module which is interested in Events must register itself with Dispatcher with uniq `sys` id (ExplainSys, DashSys, NotificationSys) 
- Dispatcher routes (forwards) events to corresponding System by `sys` id.
- Dispatcher will filter events if `dst` is set and does not correspond to Dispatcher "id". Dispatcher loads `id` from .env ((DISPATCHER_ID) or from Settings. By default it is not set which means all events are routed.
- Each module must have correspnding ExplainSys, DashSys, NotificationSys processors (in corresponding files) to process events
- Each modules knows how to parse `Event.cmd` and `Event.data`
- "Dispatcher" module must have its own UI with "Dispatcher" Menu and Overview table with all Events
- Dispatcher UI must show statistics (totla messages, total by `typ`, total by `sys` ) and show last 10 messages in a table.
  Since messages are asynchronous, it must support live update when new message arrives to the dispatcher
- Do not duplicate code, don't re-impelemt if component/module/functionality already exists. Research all componentns and modules in the codebase first. Keep the same style for paging, filters, search, table navigation

## Testing

- Create and maintain a set of "Event" json files with preconfigured Notifications Events and Commands to be used by websocket service  (Release Info Notification, Service Downgrade Warning Nofication)
- Create scripts to run websocket with different preconfigured Events
