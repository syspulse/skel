# ingest-flow

Data Ingestion Flow 

__feed__ -> [source] -> [decode] -> [transform] -> [sink] -> __output__

## Feeds

1. ```stdin://``` - from stdin
2. ```http://```  - Outgoing HTTP connection
3. ```file://```  - From single file

## Output

1. ```stdout://``` - to stdout
2. ```file://```   - single file
3. ```hive://```   - Hive style file (support for subdirs and Time pattern)


### Examples

Ingest from File into Hive based directory:

```
./run-ingest.sh -f file://data/0001.csv -o "hive://output/{YYYY-mm-DD}/data.log"
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

