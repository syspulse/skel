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
./run-tag.sh ingest -f dir://store/
```

Ingest into Elastic:

```
./run-tag.sh ingest -f ./store/tags-100.csv -o elastic://localhost:9200/tag
```

## Run server

With dir as `datastore`:

```
./run-tag.sh server -d dir://store/
```

### Run in Docker with mapped datastore

__NOTE__: docker maps `/data` to __$DATA_DIR__

```
OPT=-Dgod DATASTORE='dir:///data' DATA_DIR=`pwd`/store ../tools/run-docker.sh
```

## Search

Search goes over all labels and returns sorted tags by score. 

Labels must be full term (word). E.g `uni` and `uniswap` are different tags !

Search is case insensitive !

## Search tag

```
./tag-search.sh uniswap
```

