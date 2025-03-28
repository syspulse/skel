# skel-video

Video Ingest and Search

----

## Local Elastic Setup

General instructions: [../skel-db/elastic](../../skel-db/elastic)

Create index:

```
../skel-db/elastic/db-create-index.sh video `pwd`/elastic/schema-video.json
```

## Ingest Data

There are two commands:

1. ingest-old       - old ingest where ```-d``` controls the sink
2. ingest           - new pipepile where ```-o``` expects pipeline uri://

Check what will be ingested (TMS format to stdout)

```
./run-video.sh ingest -f file://feed/tms-100.xml
```

Ingest into Elastic from TMS feed:

```
./run-video.sh ingest -f ./feed/tms-100.xml -o elastic://localhost:9200/video
```

## Scan

Scan all videos from files datastore (regxp expression)

__NOTE__: file:// datastore reads all files in directory (not recursive)

```
./run-video.sh search 'The.*" -d file://store/
```


Scan all videos from index __video__ with match_all

```
./run-video.sh scan -d elastic
```

## Search one field with match (desc)

```
./run-video.sh search 'Avatar' -d elastic
```

## Search all movies with wildcards

```
./run-video.sh grep 'Star*' -d elastic
```

## Run REST server 

All skel-http standards apply


Run with Elastic datastore
```
./run-video.sh server -d elastic
```

Run with Files datastore (reads from store/ )
```
./run-video.sh server -d file
```

Run with Files datastore
```
./run-video.sh server -d file:///mnt/data/store
```

Run Search query against server:

```
./video-search.sh The | jq .
```

Run Type-ahead query:

```
./video-typing.sh Sta | jq .
```
