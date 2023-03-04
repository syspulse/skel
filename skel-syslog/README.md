# skel-syslog

Product Auditlog (Syslogs) for system important events

NOTE: [skel-video](../skel-video) is usually first to have updates

----

## Local Elastic Setup

General instructions: [../skel-db/elastic](../../skel-db/elastic)

Create index:

```
../skel-db/elastic/db-create-index.sh syslog `pwd`/elastic/schema-syslog.json
```

## Ingest Data

```
./run-syslog.sh ingest -f ./feed/1.csv -d elastic
```

## Run Yell service

```
./run-syslog.sh server -d elastic
```

## Get Data from API

Get all "User" area syslogs

```
./syslog-search.sh User
```

## Scan

Scan all syslogs from index __syslog__ with match_all

```
./run-syslog.sh scan
```

## Search one field with match (desc)

```
./run-syslog.sh search 'ingest soruce'
```

## Search all users in syslogs for specific domain

```
./run-syslog.sh grep '*@domain.org'
```
