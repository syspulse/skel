# skel-lake

## Livy

Check livy jobs

```
./run-job.sh livy all
```

Run Livy pipeline

```
./run-job.sh livy pipeline Test-Job file://test-5.py 'file="s3a://data/transactions/2023/03/01"'
```

## Jobs

Start server

```
GOD=1 ./run-job.sh server
```

Submit processing pipeline:

```
cat inputs.json 
{
  "from_date_input": "\"2023-03-31\"",
  "to_date_input": "\"2023-03-31\"",
  "token_address": "\"0x1f9840a85d5af5bf1d1762f925bdaddc4201f984\"",
  "chain": "\"ethereum\""
}

./job-submit.sh Holders-1 file://holders.py inputs.json
```

Check execution:

```
./job-find.sh running
```


