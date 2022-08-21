# skel-ingest

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



## Telemetry Collection

Prometheus Telemetry

<img src="doc/Skel-Architecture-skel-ingest.png" width="500">


---
## Prometheus Visualization

Grafana is the primary visualization dashboard

<img src="doc/scr-prometheus-grafana.png" width="850">

