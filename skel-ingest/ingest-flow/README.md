# ingest-flow

Data Ingestion Flow 

All input data is ByteString

All output data is `Ingestable` and must provide `Json` and `Parquet` serializers

__feed__ -> [source] -> [decode] -> [transform] -> [sink] -> __output__

## Input Feeds

1. ```stdin://```                               - from stdin
2. ```http://host:port/api```                   - HTTP Client from remote HTTP server. Mulitple HTTP servers can be used via `,`
3. ```file://dir/file```                        - From single file. This is also default option if uri:// prefix is not used
                                                  Support multiple source with comma: (`http://host1,http://host2`)
4. ```kafka://broker:9092/topic/group/offset``` - From Kafka `offset` == latest,earliest
5. ```dir://dir```                              - From directory (reads all files)
6. ```dirs://dir```                             - From directories (reads all files from all subdirs)
7. ```null://```                                - No source
8. ```tick://inteval://{source}```              - Tick cron. ex: `tick://1000://http://localhost:8300`
8. ```cron://expr://{source}```                 - Crontab cron. ex: `cron://expr://http://localhost:8300`
                                                  `expr` is either configured scheduler name (applicaion.conf) or cron expression
                                                  __NOTE__: because of bash, use this format: `'cron://*/1_*_*_*_*_?'`
9. ```null://```                                - No source
10. ```clock://```                              - Clock ticker (with optional frequency). Similar to `tick` but not source
11. ```server://host:port/path```               - HTTP Server listener (Webhook). Server accepts `POST` and always responds "200" (behaves like webhook)
12. ```tcp://host:port```                       - Tcp client from remote TCP Server
13. ```tail://file```                           - Tail file
14. ```tails://dir```                           - Tail directory for new files

## Output Feeds

1. ```stdout://```, ```stderr://```                          - std pipes
2. ```file://dir/file```                                     - to single file (time patterns supported)
3. ```hive:///data/{YYYY}/{MM}/{dd}/file-{HH:MM:SS}.log```   - Hive style file (support for subdirs and Time pattern)
4. ```elastic://host:9200/index```                           - To Elastic index
5. ```kafka://broker:9092/topic```                           - To Kafka
6. ```null://```                                             - Sink.ignore
7. ```fs3://```                                              - file without APPEND (S3 object store)
8. ```json://```                                             - Json to stdout (uses Spray to convert to AST and prettyprint)
9. ```csv://```                                              - CSV to stdout
10. ```log://```                                             - Calls toLog on Ingestable
11. ```filenew://```                                         - Generate new file for every event (use time formatters)
12. ```files://```                                           - Limit file by size
13. ```parq://{file}```                                      - Parquet Format file (time patterns supported)
14. ```http://host:port```                                   - HTTP Client to remote HTTP server which accepts `POST`
15. ```jdbc://db```                                          - Exeperimental JDBC (only flat object)

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

To remote HTTP server with `\r\n`:
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

Pipeline to remote HTTP Server with periodic cron (`cron://exprName`)
```
./run-ingest.sh -f cron://EverySecond://http://localhost:8300 -o stdout://
```

Pipeline to HTTP with periodic tick (`tick://initial,interval`)

```
./run-ingest.sh -f tick://0,1000://http://localhost:8300 -o stdout://
```

Ingest from Kafka to files:
```
./run-ingest.sh -f  kafka://localhost:9092/transactions/g1 -o hive:///mnt/share/data/spark/eth/{YYYY}/{MM}/{dd}/transactions-{HH_mm_ss}.log
```

Run Clock with mulitple processors:

Clock is very similar to tick://, but does not allow to pipeline

```
./run-ingest.sh -f clock:// print dedup
```

Listen for HTTP POST requests

```
./run-ingest.sh -f server://0.0.0.0:8080/webhook --delimiter=

echo "test" |curl -i -X POST --data @-  http://localhost:8080/webhook

```

Push to Postgres

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


---

## Akka

https://medium.com/@yuvalshi0/batch-transformation-with-akka-streams-6a9b58b5e29c
