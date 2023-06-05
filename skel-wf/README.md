# skel-wf

## WorkFlow Engine Prototype

Features:

- Long-running workflow optimized (hours and days)
- State persistance and recovery
- Event driven. However not suitable for high-performance streaming
- Private directory for large data processing via files
- Data passing via DataEvent map (not for large datasets passing <10M)
- Lightweight and integration into existing Streaming Pipeines as seperates phases (Execs)
- Different runtime engines (Threads,akka-actors)


## DSL Assembly

DSL Assembly is a script to assemble simple Workflows.

Complex workflows should be designed with UI

One-pass Workflow with two logger with termination:

```
./run-wf.sh wf assemble wf-1 'F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))->F-3(TerminateExec())'
```

Star the Worfklow:

```
./run-wf.sh runtime run wf-1
```

Runnning workflow has unique ID. 

Find WID with checking the status:

```
./run-wf.sh runtime status
```

Stop the Workflow:

```
./run-wf.sh runtime stop <WID>
```

Assemble, Run, Wait, Stop and Remove in one pass (JSON Parsing example)

```
echo "{\"id\":\"100\"}" | ./run-wf.sh wf run WF00001 'F-0(ScriptExec(script=file://script-json1.sc))->F-1(LogExec(log.level=WARN))'
```

----
### Workflow DSL Examples

1. Infinite Cron Workflow
```
./run-wf.sh wf assemble wf-2 'F-0(CronExec(cron=1000))->F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))'
```

2. Infintite Loop with controllable throttling

```
./run-wf.sh wf assemble wf-3 'F-1(LogExec(sys=1,log.level=WARN))->F-2(ThrottleExec(throttle=1000)->F1[in-0])'

./run-wf.sh runtime run wf-3

./run-wf.sh runtime status

./run-wf.sh runtime emit wf-3-1678978472628 F-1 throttle=500
```

3. External events workflow (based on FifoExec )

Create FIFO:
```
mkfifo /tmp/skel-wf/test/FIFO
```

Assembly:
```
./run-wf.sh wf assemble wf-4 'F-1(FifoExec(fifo.file=/tmp/skel-wf/test/FIFO))->F2(LogExec())'
```

Start:
```
./run-wf.sh runtime run wf-4
./run-wf.sh runtime emit wf-4-1679045999379 F-1
```

Emit External Event:
```
echo "Event" >/tmp/skel-wf/test/FIFO
```

4. Workflow with exploding data into List and then Collecting

```
./run-wf.sh wf assemble wf-5 'F1(RandExec())->F2(SeqExec())->F3(CollExec())->F4(LogExec())->F5(TerminateExec())'
./run-wf.sh runtime spawn wf-5
./run-wf.sh runtime emit wf-5-1679057202485 F1 'rand.max=100'
```


5. Parameterzied inputs and variable replacements

```
./run-wf.sh wf assemble wf-7 'F1(CronExec(cron=1000))->F2(HttpClientExec(http.uri=http://localhost:8300))->F3(LogExec(sys=2))'

cat inputs.conf 
http.body={"from":"2023","addr":"{addr}"}

./run-wf.sh runtime emit wf-7-1681759525495 F1 file://inputs.conf 'addr=0x1111'
```

6. Flow with parsing `json` file and producing list of ids

```
./run-wf.sh wf run WF00001 'F-0(FileReadExec(file=data/RSP-IDs-3.json))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F-4(LogExec(log.level=WARN))'
```

7. Flow with asking HTTP server for json and producing list of ids

```
./run-wf.sh wf run WF00001 'F-0(HttpClientExec(http.uri=http://localhost:8300))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F-4(LogExec(log.level=WARN))'
```

8. Complex worklfow with ID based ingest

Save into aggregated file:
```
./run-wf.sh wf run WF00001 'F-0(FileReadExec(file=data/RSP-IDs-3.json))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F5(HttpClientExec(http.uri=http://localhost:8301))->F6(JoinExec(join.max={input.size},join.symbol=\n))->F7(FileWriteExec(file=data/FILE-{sys.timestamp}.json))'
```

Save into separate files with unique id:

```
./run-wf.sh wf run WF00001 'F-0(FileReadExec(file=data/RSP-IDs-3.json))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F4(VarExec(var.name=id))->F5(HttpClientExec(http.uri=http://localhost:8301/{id}))->F6(JoinExec(join.max=1,join.symbol=\n))->F7(FileWriteExec(file=data/FILE-{id}-{sys.timestamp}.json))'
```

Get Ids from External service:

```
./run-wf.sh wf run WF00001 'F-0(HttpClientExec(http.uri=http://localhost:8300/))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F4(VarExec(var.name=id))->F5(HttpClientExec(http.uri=http://localhost:8301/{id}))->F6(JoinExec(join.max=1,join.symbol=\n))->F7(FileWriteExec(file=data/FILE-{id}-{sys.timestamp}.json))'
```

```
./run-wf.sh wf run WF00001 'F-0(HttpClientExec(http.uri=https://api.coingecko.com/api/v3/coins/list))->F-1(ScriptExec(script=file://script-json-ids.sc))->F-3(SplitExec(split.symbol=;))->F4(VarExec(var.name=id))->F5(HttpClientExec(http.uri=https://api.coingecko.com/api/v3/coins/{id}?localization=false&tickers=false&market_data=false&community_data=false&developer_data=false&sparkline=false))->F6(JoinExec(join.max=1,join.symbol=\n))->F7(FileWriteExec(file=data/FILE-{id}-{sys.timestamp}.json))'
```
