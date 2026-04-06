# Workflow engine 

Temporal

NOTE: Current implementation doe not allow flexible generic workflow step configuration during the run.

## Run temporal dev server

[README-temporal.md](README-temporal.md)


## Run worker

```
APP_EXEC=bloop ./run-temporal.sh por-worker --engine=demo://
```

## Signals

1. Start workflow which waits for singal

```
APP_EXEC=bloop ./run-temporal.sh por-start flow-5 --por.pol.signal=api
```

2. Send signal to workflow

```
APP_EXEC=bloop ./run-temporal.sh temporal signal <run_id> pol '{"data":100}'
```

## Indexes (Attributes)

1. Initialize Temporal with search attributes:

```
./run-temporal.sh temporal init tid:Int pid:Int sys:Keyword proj:Keyword
```

2. Verify registration:

```
temporal operator search-attribute list --namespace default
```

3. Query by tenant:

```
./run-temporal.sh temporal query 'tid=1'
```



