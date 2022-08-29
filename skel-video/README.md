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

Check what will be ingested:

```
./run-video.sh ingest -f file://feed/1.csv -o stdout://
```

Ingest into Elastic:

```
./run-video.sh ingest -f ./feed/1.csv -o elastic://localhost:9200/video
```


## Scan

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

```
./run-video.sh server -d elastic
```

Run Search query:

```
./video-search.sh The | jq .
```

Run Type-ahead query:

```
./video-typing.sh Sta | jq .
```
