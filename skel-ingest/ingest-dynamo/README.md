# ingest-dynamo

DynamoDB Data Ingestion Flow

----

## Local Dynamo DB Setup

Go to [../../skel-db/dynamodb](../../skel-db/dynamodb) for instructions

## Credentials

Setup AWS Credentials. 

Code uses default AWS provider which reads credentials from standard sources (AWS env.var,.aws/crednetials) and not application config

## Ingest Data

Ingest TMS feed with default config

```
./run-dynamo.sh ingest --feed tms-100.xml
```

## Scan Data

Scan all objects with overriden config

```
./run-dynamo.sh scan --dynamo http://localhost:8100 --table MOVIE
```

## Query object by Partition Key 

Ignores Sort Key and will return multiple Objects

```
./run-dynamo.sh query EP008048630217
```

## Get object by Primary Key (Partition + Sort)

NOTE: it should return only a single Object

```
./run-dynamo.sh get EP008048630217
```
