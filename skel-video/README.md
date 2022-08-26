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

Check what will be ingested:

```
./run-video.sh ingest -f ./feed/1.csv -d stdout
```

Ingest into Elastic:

```
./run-video.sh ingest -f ./feed/1.csv -d elastic
```

## Scan

Scan all videos from index __video__ with match_all

```
./run-video.sh scan
```

## Search one field with match (desc)

```
./run-video.sh search 'Avatar'
```

## Search all movies with wildcards

```
./run-video.sh grep 'Star*'
```
