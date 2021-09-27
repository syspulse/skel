## skel-cron

----

### Running with Cron expression

__NOTE__: To avoid auto-gobbling and expansion it scrips, cron expression must be quoted:


0. Run every second with default Scheduler

```
./run.sh --crontab.cron="*/1 * * * * *"
```

1. Run every 14:30 with default Scheduler

```
./run.sh --crontab.quartz=default --crontab.cron="* 30 14 * * *"
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
./run.sh --crontab.quartz=quartz-1
```