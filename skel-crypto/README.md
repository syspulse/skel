
# Get ABI

```
# The base64 key below is for contract CCPYZFKEAXHHS5VVW5J45TOU7S2EODJ7TZNJIA5LKDVL3PESCES6FNCI
curl -X POST https://soroban-testnet.stellar.org \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0", "id": 1,
    "method": "getLedgerEntries",
    "params": {
      "keys": ["AAAABgAAAAGfjJVEBc55drW3U87N1Py0Rw0/nlqUA6tQ6r28khEl4gAAABQAAAAB"]
    }
  }' | jq .
```