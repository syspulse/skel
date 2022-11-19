# skel-spark

Spark Skels

1. [spark-dynamodb](spark-dynamodb) - DynamoDB ingestion Spark job
2. [spark-convert](spark-convert)   - Convert formats with Spark (CSV->Parquet)



## Simple scripts

### Get versions

```
spark.version
util.Properties.versionString
```

### Filter out null values

```
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

df.filter(df("value").isNotNull))
```

### Long timestamp (Unix) to Date

```
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types._

df.select("ts","value").withColumn("date",to_date(df("ts").cast(TimestampType)))
```

### Date to specific format (for groupBy)
```
df.select("ts","value").withColumn("date",to_date(df("ts").cast(TimestampType),"yyyy/MM/dd"))
```