# Telemetry Server

Collects telemetry from Blockchains

## Data Stores

| provider | description |
|-------------|--------------|
| redis://    | Redis       |
| mem://      | Memory |
| dir://      | Dir file |

## Chains

Chains are specified in the following format delimited by comma

`{chain_name}={chain_id}={feed}`
`{chain_name}={feed}`

Example:

`ethereum=kafka://haas-dev-kafka1.hacken.dev:9092/ethereum.mainnet.tx`

[application-telemtry.conf](application-telemetry.conf) provides default configuraiton


## Run

Collect from two chains and feed to stdout

```
./run-telemetry.sh --chain=ethereum=kafka://localhost:9092/ethereum.tx,ethereum_sepolia=kafka://localhost:9092/sepolia.tx
```


Collect from chains in `application.conf` and store to Redis `10` space

```
./run-telemetry.sh --datastore=redis://localhost:6379/10
```

### Ingest to HTTP Endpoint

```
ACCESS_TOKEN=111111111 ./run-telemetry.sh -o "http://POST@{ACCESS_TOKEN}@localhost:8300" --format=json
```
