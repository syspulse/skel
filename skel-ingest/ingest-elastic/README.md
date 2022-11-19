# ingest-elastic

DynamoDB Data Ingestion Flow

----

## Local Elastic Setup

Go to [../../skel-db/elastic](../../skel-db/elastic) for instructions

## Credentials


## Ingest Data

Ingest TMS feed with into __video__ index

```
./run-elastic.sh ingest --feed tms-100.xml --index video
```

## Scan Data

Scan all objects from index __video__ with match_all

```
./run-elastic.sh scan --index video
```

## Search one field with match (title)

```
./run-elastic.sh searches 'Spider' --index video
```

## Search multiple fields with wildcards

```
./run-elastic.sh wildcards 'Sp*' --index video
```