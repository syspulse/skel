# skel-convert


Convert from CVS -> Parquet 

Batch with 10000 records into parquets (requires ram!) with 16 workers

```
./run-spark-convert.sh --input ./data/csv/ --output ./data/parquet/ --batch 10000 --par 16
```

Convert from CVS -> Parquet with low memory (streaming fashion)

```
./run-spark-convert.sh --input ./data/csv/ --output ./data/parquet/ --batch 1 --par 1
```

Convert from CVS -> Parquet with type mapping

```
./run-spark-convert.sh --input ./data/csv/ --output ./data/parquet/ --map 'number:LongType;difficulty:DecimalType(38,0);total_difficulty:DecimalType(38,0);size:LongType;gas_limit:LongType;gas_used:LongType;timestamp:LongType;transaction_count:LongType;base_fee_per_gas:LongType'
```

Convert from CVS -> Parquet with type mappings in config file

```
./run-spark-convert.sh --input ./data/csv/ --output ./data/parquet/ --map @parquet.blocks
```
application.conf:
```
parquet {
  blocks="number:LongType;difficulty:DecimalType(38,0);total_difficulty:DecimalType(38,0);size:LongType;gas_limit:LongType;gas_used:LongType;timestamp:LongType;transaction_count:LongType;base_fee_per_gas:LongType"
  transactions="nonce:LongType;block_number:LongType;transaction_index:LongType;value:DecimalType(38,0);gas:LongType;gas_price:LongType"
}
```

Convert one file:
```
./run-spark-convert.sh --input ./data/csv/transactions.csv --output ./data/parquet/transactions --map @parquet.transactions
```

Convert on AWS S3 

```
./run-spark-convert.sh --input s3a://bucket/export/csv/ --output s3a://bucket/export/parquet/
```

JVM and Spark Session tuning:

```
JAVA_OPTS="-Xmx4g -Xms4g" ./run-spark-convert.sh --spark.executor.memory 4g --spark.driver.memory 1g --spark.cores.max 4
```

Docker (with JVM tuning):

```
JAVA_OPTS="-Xmx8g -Xms8g" docker run --rm --env JAVA_OPTS -v /mnt/share/data/spark:/data syspulse/spark-convert --input /data/csv --output /data/parquet/
```
