# skel-wf

## WorkFlow Engine Prototype

Workflow primary features:

- long-running workflow (hours and days)
- large data processing via files
- data passing via context state (moderate size)
- lightweight and integration into existing Streaming Pipeines as seperates phases (Execs)
- Different simple runtimes (Threads,akka-actors)



## DSL 

One-pass Flow with two logger with termination:

```
./run-wf.sh wf create wf-1 'F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))->F-3(TerminateExec())'
```

Infinite Cron:
```
./run-wf.sh wf assemble wf-2 'F-0(CronExec(cron=1000))->F-1(LogExec(sys=1,log.level=WARN))->F-2(LogExec(sys=2))'
```

Infintite Loop with throttling

```
./run-wf.sh wf assemble wf-3 'F-1(LogExec(sys=1,log.level=WARN))->F-2(ThrottleExec(throttle=1000)->F1[in-0])'
```

