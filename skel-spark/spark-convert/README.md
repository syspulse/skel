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

Convert on AWS S3 

```
./run-spark-convert.sh --input s3a://bucket/export/csv/ --output s3a://bucket/export/parquet/
```

Docker:

```
docker run --rm -v /mnt/share/data/spark:/data syspulse/spark-convert --input /data/csv --output /data/parquet/
```