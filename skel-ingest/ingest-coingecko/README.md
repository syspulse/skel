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
By default it transforms into `skel` Coin/Token object with very few fields

Entity (`--entity`)
`coin` - coin object (or many coins delimited by newline)
`coins` - coins list (simple array of coin IDs). NOTE: It is not array of coin objects !
`raw.coin` - raw coingecko CoinData output
`raw.coins` - raw coingecko CoinData output from coinslist.

entity parses input to validate json. It is possible to control this behavior with `--parser`

Parsers:
`json`  - standard super fast parser to parse all known fields
`ujson` - slow parsing fields one by one. It is useful when default parsing fails because of format incompatibility
`none`  - limited parsing performed to extract `id`. Only works with `--entity=raw`
          


### Ingest RAW data slowly (to avoid 429) from Coingecko

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/ALL-1.json" --throttle=10000
```

### Ingest specific coin json from coingecko:

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// --filter=weth
```


### Ingest specific mulitple coins jsons from coingecko:

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// --filter=weth,steth --throttle=5000
```

### Ingest RAW data from Coingecko PRO and add intelligent parsing to avoid unsupported fields

```
./run-coingecko.sh pipeline -e raw.coin -f "coingecko://$CG_API_KEY" -o null:// --parser=ujson
```

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/ALL-1.json" --throttle=10000
```

### Ingest RAW data fast (pro subscription) from Coingecko and write evey coin to separate files with a pattern

```
./run-coingecko.sh pipeline -e raw.coins -f coingecko:// -o "files://./data/{ID}-{YYYY}_{MM}_{DD}.json" --throttle=250
```

### Ingest specific Coins from Coingecko and write all into one file

```
./run-coingecko.sh pipeline -e raw.coins -f cg:// -o "file://./data/SELECTED-1.json" --filter=bitcoin,ethereum --throttle=10000
```

