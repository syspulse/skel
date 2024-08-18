## skel-cron

----

### Running with Cron expression

http://www.cronmaker.com/?0


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

## Quartz Expressions:

- https://www.freeformatter.com/cron-expression-generator-quartz.html
- http://www.cronmaker.com/?0


Every second: `* * * ? * *`

Every two minutes: `0 */2 * ? * *`

Every hour at minutes 15, 30 and 45: `0 15,30,45 * ? * *`

Hourly: `0 0 0/1 1/1 * ? *`

Every minute: `0 0/1 * 1/1 * ? *`

Every 10 minutes: `0 0/10 * 1/1 * ? *`

Daily: `0 0 12 1/1 * ? *`


