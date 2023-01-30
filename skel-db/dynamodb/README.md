# DynamoDB


### Local Dynamo DB

Local DynamoDB Docker instance with Table sharing
```
cd skel-db/dynamodb
./docker.sh

```

## Set up DB

Use __db-cred.sh__ for AWS Credentials

Local DynamoDB requires the following credentials to work
```
AWS_ACCESS_KEY_ID=<random generated>
AWS_SECRET_ACCESS_KEY=<random generated>
AWS_REGION=localhost
```


All commands for local acceept DB_URI enviroment variable to override default (http://localhost:8100):

Example:
```
DB_URI=http://dynamodb-host:8100 db-scan.sh

```

### Create Table from schema

Table name is specified in schema.json

```
cd skel-db/dynamodb
./db-create.sh [schema.json]

```

__NOTE__: Schema can be extracted from existing Table (e.g. created with [NoSQL Workbench](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/workbench.html))


```
cd skel-db/dynamodb
./db-schema.sh <TableName>

```

### Truncate Table (will drop and recreate table)

```
cd skel-db/dynamodb
./db-trunc.sh <TableName>

```

### Show all objects in Table

```
cd skel-db/dynamodb
./db-scan.sh <TableName>

```