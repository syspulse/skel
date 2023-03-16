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

