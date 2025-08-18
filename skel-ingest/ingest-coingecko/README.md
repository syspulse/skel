# ingest-coingecko

URI Format:

`cg://` - Free API
`coingecko://` - Pro API

## Simple testing

### Get all coins

```
./run-coingecko.sh coins "cg://"
```

### Get specific coins

```
./run-coingecko.sh coin cg:// bitcoin
```


## Pipeline

Pipelines allow to make stream data with different transformations.

### Ingest RAW data slowly (to avoid 429) from Coingecko

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/ALL-1.json" --throttle=10000
```

### Ingest RAW data from Coingecko PRO and add intelligent parsing to avoid unsupported fields

Parsers:
`json` - standard super fast parser to parse all known fields
`ujson` - slow parse fields one by one, bypasses new fields

```
./run-coingecko.sh pipeline -e raw.coin -f "coingecko://$CG_API_KEY" -o null:// --parser=ujson
```

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/ALL-1.json" --throttle=10000
```

### Ingest RAW data fast (pro subscription) from Coingecko and write evey coin to separate files

```
./run-coingecko.sh pipeline -e raw.coins -f coingecko:// -o "file://./data/{ID}.json" --throttle=250
```

### Ingest specific Coins from Coingecko and write all into one file

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/SELECTED-1.json" --filter=bitcoin,ethereum --throttle=10000
```

