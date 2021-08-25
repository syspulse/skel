# eth-key 

OpenSSL based utils for ECDSA keys

1. Keys generation (and address)
2. Signing
3. Signature verification


### Examples

Generate Keys with Ethereum address
```
./key-generate-address.sh
```

Generate Keys with key files for signing:
```
./key-generate-files.sh
```

Create signature:

```
SK_FILE=sk.pem ./key-sign <file>
```

Verify signature:
```
PK_FILE=pk.pem ./key-verify <file> <signature-file>
```


## Libraries and Credits

1. Keystore Format: [https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition](https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition)