# skel-crypto

Cryptography collection

__NOTE__: for PoC and prototyping

1. [eth-openssl](eth-openssl) - OpenSSL ECDSA Keys for Ethereum
2. [eth-keystore](eth-keystore) - Ethereum Keystore
3. [keccak](keccak) - Keccak256 tool

----

## Tools

### keystore

Generate Eth1 keystore.json with SK = 0x01
```
./run-keystore.sh write -m 0x01
```

Generate Eth1 keystore.json with mnemomnic
```
./run-keystore.sh write -m 'word1 ... word24'
```

Generate Eth2 keystore.json with mnemomnic
```
./run-keystore.sh write -t eth2 -m 'word1 ... word24'
```


## Certificates

### SSL Certificates Architecture

<img src="doc/CA.jpg" width="900">

