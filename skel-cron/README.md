## skel-cron

----

### Running with Cron expression

__NOTE__: Unfortunately, it is impossible to pass quoted arguments through 2 bash scripts.
Bash removes quotes and second script loses the boundary of the quoted expression

Cron expression must be passed as `CRON_EXPR="expression"`


Run every second with default Scheduler

```
CRON_EXPR="*/1 * * * * ?" ./run-cron.sh
```

Run every 14:30 with default Scheduler

```
CRON_EXPR="* 30 14 * * *" ./run-cron.sh --cron.quartz=default
```

Run with *protected* expression 

```
./run-cron.sh --cron.quartz=default --cron.expr='*/1_*_*_*_*_?'
```

2. Run with custom Scheduler

__application.conf__:

```
quartz-1 {
  org.quartz.threadPool.threadCount=5
  org.quartz.scheduler.instanceName=Scheduler-Quartz-1
  org.quartz.jobStore.class=org.quartz.simpl.RAMJobStore
}
```

```
./run.sh-cron --cron.quartz=quartz-1
```