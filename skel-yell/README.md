# skel-yell

AuditLog for system important events (yells)

----

## Local Elastic Setup

General instructions: [../skel-db/elastic](../../skel-db/elastic)

Create index:

```
../skel-db/elastic/db-create-index.sh yell `pwd`/elastic/schema-yell.json
```

## Ingest Data

```
./run-yell.sh ingest -f ./feed/1.csv -d elastic
```

## Run Yell service

```
./run-yell.sh server -d elastic
```

## Get Data from API

Get all "User" area yells

```
./yell-search.sh User
```

## Scan

Scan all yells from index __yell__ with match_all

```
./run-yell.sh scan
```

## Search one field with match (desc)

```
./run-yell.sh search 'ingest soruce'
```

## Search all users in yells for specific domain

```
./run-yell.sh grep '*@domain.org'
```
