# skel-tag

Tags

----

## Local Elastic Setup

General instructions: [../skel-db/elastic](../../skel-db/elastic)

Create index:

```
../skel-db/elastic/db-create-index.sh tag `pwd`/elastic/schema-tag.json
```

## Ingest Data

```
./run-tag.sh ingest -f file://feed/tms-100.xml
```

Ingest into Elastic:

```
./run-tag.sh ingest -f ./feed/tms-100.xml -o elastic://localhost:9200/tag
```

## Scan

Scan all tags from files datastore (regxp expression)

__NOTE__: file:// datastore reads all files in directory (not recursive)

```
./run-tag.sh search 'The.*" -d file://store/
```


Scan all tags from index __tag__ with match_all

```
./run-tag.sh scan -d elastic
```

## Search one field with match (desc)

```
./run-tag.sh search 'Avatar' -d elastic
```

## Search all movies with wildcards

```
./run-tag.sh grep 'Star*' -d elastic
```

## Run REST server 

All skel-http standards apply


Run with Elastic datastore
```
./run-tag.sh server -d elastic
```

Run with Files datastore (reads from store/ )
```
./run-tag.sh server -d file
```

Run with Files datastore
```
./run-tag.sh server -d file:///mnt/data/store
```

Run Search query against server:

```
./tag-search.sh The | jq .
```

Run Type-ahead query:

```
./tag-typing.sh Sta | jq .
```
