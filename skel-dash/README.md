# Dash

## Data Sources

| name | source |
| ---- | ----- |
| dune | Dune API |
| elastic | ElasticDB |
| coingekco | Coingecko API |
| sql | JDBC Database |
| test | For testing only |


### Dune Datasource

```
GOD=1 ./run-dash.sh --ds='dune://?compress=true'
```

### Elastic Datasource

```
GOD=1 ./run-dash.sh --ds="ess://{ES_USER}:{ES_PASS}@localhost:9200"
```

### SQL Datastource

SQL is a standard JDBC connector

```
GOD=1 ./run-dash.sh --ds="jdbc:postgres://{DB_USER}:{DB_PASS}@{DB_HOST}/{DB_NAME}?TimeZone=UTC"
```

### Many Engines (flexible selection in Request)

`application-1.conf`:
```
ds = """many://
  test://,
  ess://{ES_USER}:{ES_PASS}@{ES_HOST}?compress=true,
  dune://{DUNE_API_KEY},
  coingecko://{CG_API_KEY},
  postgres://{DB_USER}:{DB_PASS}@{DB_HOST}/{DB_NAME}?TimeZone=UTC
"""
```

```
GOD=1 ./run-dash.sh --conf=conf/application-1.conf
```



## Rqeuests

Test request (must run `test://`):

```
./dash-data.sh 5178035 test
```

Elastic request (must run `es://`):

```
QUERY='coid:4976 AND deid:11881 AND se:INFO AND ts:>=2025-03-13' ./dash-elastic.sh "detector-alert-search,detector-event-search"
```

Elastic request with SQL:

__NOTE__: QUERY must be enclosed in `""` because it is json

```
TYP=sql QUERY='"SELECT COUNT(*) FROM detector-event-search"' ./dash-elastic.sh
```

## DB Datastore

### Setup DB

1. MySQL

```
cd ./db/mysql
${SKEL_HOME}/skel-db/mysql/db-create.sh
```

2. Postgres

```
cd ./db/postgres
${SKEL_HOME}/skel-db/postgres/db-create.sh
```

Apply env:

```
source ./env.local
```

Run with DB datastore:

```
./run-dash.sh --datastore=jdbc://postgres
```

`postgres` config is defined in Application config (e.g. `conf/application.conf`)

If timezone is set incorrectly and Postgres does not understand it, run with env or supply in uri (`postgres://{DB_USER}:{DB_PASS}@{DB_HOST}/{DB_NAME}?TimeZone=UTC`)

```
TZ=UTC ./run-dash.sh --datastore=jdbc://postgres
```

