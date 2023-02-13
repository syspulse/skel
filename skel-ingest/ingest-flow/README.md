# ingest-flow

Data Ingestion Flow 

__feed__ -> [source] -> [decode] -> [transform] -> [sink] -> __output__

## Feeds

1. ```stdin://```                               - from stdin
2. ```http://host:port/api```                   - Outgoing HTTP connection
3. ```file://dir/file```                        - From single file
4. ```kafka://broker:9092/topic/group```        - From Kafka

## Output

1. ```stdout://```, ```stderr```                             - std pipes
2. ```file://dir/file```                                     - to single file
3. ```hive:///data/{YYYY}/{MM}/{dd}/file-{HH:MM:SS}.log```   - Hive style file (support for subdirs and Time pattern)
4. ```elastic://host:9200/index```                           - To Elastic index
5. ```kafka://broker:9092/topic```                           - To Kafka
6. ```null://```                                             - Sink.ignore
7. ```fs3://```                                              - s3 like without APPEND support
8. ```json://```                                             - Json to stdout (uses Spray to convert to AST and prettyprint)
9. ```csv://```                                              - CSV to stdout


### Examples

Ingest from File into Hive based directory:

```
./run-ingest.sh -f file://data/0001.csv -o "hive://output/{YYYY-MM-dd}/data.log"
```

Ingest from HTTP into stdout

```
./run-ingest.sh -f http://localhost:8100/data -o stdout://
```

Use special delimiter

Windows files:
```
./run-ingest.sh -f file://data/win.csv --delimiter=`echo -e $"\r"`
```

HTTP server with `\r\n`:
```
./run-ingest.sh -f http://localhost:8100/data --delimiter=`echo -e $"\r\n"`
```

Large lines (don't fit into default stream buffer)

```
./run-ingest.sh -f file://data/0001.csv --buffer=100000
```

Pull from Kafka into file

```
./run-ingest.sh -f kafka://localhost:9200/topic.2/group.2 -o file:///tmp/file.data
```

Pipeline with throttling 1 msg/sec (throttle == 1000 msec)
```
./run-ingest.sh -f kafka://localhost:9200/topic.2/group.2 --throttle=1000
```

Pipeline to HTTP with periodic cron (`cron://exprName`)
```
./run-ingest.sh -f cron://EverySecond://http://localhost:8300 -o stdout://
```

Pipeline to HTTP with periodic tick (`tick://initial,interval`)

```
./run-ingest.sh -f tick://0,1000://http://localhost:8300 -o stdout://
```


Getting transactions from ethereum-etl into file partitions. (e.g. for Spark processing)

Run ETL:
```
ethereumetl stream -e transaction --start-block `eth-last-block.sh` --provider-uri $ETH_RPC -o kafka/localhost:9092
```

Run Ingest:
```
./run-ingest.sh -f  kafka://localhost:9092/transactions/g1 -o hive:///mnt/share/data/spark/eth/{YYYY}/{MM}/{dd}/transactions-{HH_mm_ss}.log
```