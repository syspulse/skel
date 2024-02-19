# ingest-flow

Pipeline Application Prototype demostrating Ingest Flow

### Examples

### Ingest from File into Hive based directory:

```
./run-ingest.sh -f file://data/0001.csv -o "hive://output/{YYYY-MM-dd}/data.log"
```

### Ingest from HTTP remote into stdout

```
./run-ingest.sh -f http://localhost:8100/data -o stdout://
```

### Use special delimiter

Windows files:
```
./run-ingest.sh -f file://data/win.csv --delimiter=`echo -e $"\r"`
```

### From remote HTTP server with `\r\n`:
```
./run-ingest.sh -f http://localhost:8100/data --delimiter=`echo -e $"\r\n"`
```

### Large lines (don't fit into default stream buffer)

```
./run-ingest.sh -f file://data/0001.csv --buffer=100000
```

### Pull from Kafka into file

```
./run-ingest.sh -f kafka://localhost:9200/topic.2/group.2 -o file:///tmp/file.data
```

### Pipeline with throttling 1 msg/sec (throttle == 1000 msec)
```
./run-ingest.sh -f kafka://localhost:9200/topic.2/group.2 --throttle=1000
```

### Pipeline to remote HTTP Server with periodic cron (`cron://exprName`)
```
./run-ingest.sh -f cron://EverySecond://http://localhost:8300 -o stdout://
```

### Pipeline to HTTP with periodic tick (`tick://initial,interval`)

```
./run-ingest.sh -f tick://0,1000://http://localhost:8300 -o stdout://
```

### Ingest from Kafka to files:
```
./run-ingest.sh -f  kafka://localhost:9092/transactions/g1 -o hive:///mnt/share/data/spark/eth/{YYYY}/{MM}/{dd}/transactions-{HH_mm_ss}.log
```

### Run Clock with mulitple processors:

Clock is very similar to tick://, but does not allow to pipeline

```
./run-ingest.sh -f clock:// print dedup
```

### Listen for HTTP POST requests from http client

```
./run-ingest.sh -f server://0.0.0.0:8080/webhook --delimiter=

echo "test" |curl -i -X POST --data @-  http://localhost:8080/webhook

```

### Push to Postgres

`db1` is a DB config profile in `applicaion.conf`:

```
db1 {
  dataSourceClassName=org.postgresql.ds.PGSimpleDataSource
  dataSource.url="jdbc:postgresql://localhost:5432/ingest_db"  
  dataSource.user=ingest_user
  dataSource.password=ingest_pass
}
```

```
./run-ingest.sh -f 5.log -o "jdbc://db1"
```

### WS listener sink !

```
(while [ 1 ]; do echo `date`; sleep 1;done)| ./run-ingest.sh -o server:ws://0.0.0.0:9300/ws
```

Connnect with clients (beware of websocat buffering):

```
websocat ws://localhost:9300/ws -B 1000000
websocat ws://localhost:9300/ws -B 1000000
```

__ATTENTION__: `websocat` buffers large messages to stdout (default). Use `wscat` or pipe to file and tail:

```
websocat ws://localhost:9300/ws -B 1000000 >output.log
tail -F output.log
```

---

## Akka

https://medium.com/@yuvalshi0/batch-transformation-with-akka-streams-6a9b58b5e29c
