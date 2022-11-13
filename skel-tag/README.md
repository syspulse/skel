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

## Search

Search goes over all labels and returns sorted tags by score. 

Labels must be full term (word). E.g `uni` and `uniswap` are different tags !

Search is case insensitive !

## Search tag

```
./tag-search.sh uniswap
```

