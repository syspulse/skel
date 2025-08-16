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

Use `ingest-flow`
