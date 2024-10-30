# skel-crypto

Cryptography collection

__NOTE__: for PoC and prototyping

1. [eth-openssl](eth-openssl) - OpenSSL ECDSA Keys for Ethereum
2. [eth-keystore](eth-keystore) - Ethereum Keystore
3. [keccak](keccak) - Keccak256 tool
4. ABI tools


----
## Links

https://sepolia-faucet.pk910.de/

----

## Tools

### ABI

Show ABI for Contract `json`:

```
./run-abi.sh abi abi/UNI-0x1f9840a85d5af5bf1d1762f925bdaddc4201f984.json
```

Show ABI for known contract
```
./run-abi.sh abi 0x1f9840a85d5af5bf1d1762f925bdaddc4201f984
```

Decode function call (__intput__):
```
./run-abi.sh decode 0x1f9840a85d5af5bf1d1762f925bdaddc4201f984 function 0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000
```

Decode Transfer event (__topics__):

```
./run-abi.sh decode 0xdac17f958d2ee523a2206206994597c13d831ec7 event 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef 0x000000000000000000000000cdbb7436f9d4c21b7627065d1556db29597981f4 0x00000000000000000000000080a25bb487e89e79599c9acae6dbc6b8a5f1bcdc 0x0000000000000000000000000000000000000000000000000000000000000703
```

Show Function Signature/Events Store:

```
./run-abi.sh func
./run-abi.sh event
```

Search for signature

```
./run-abi.sh func 0xa9059cbb
./run-abi.sh event 0xb2d1d3f10ca4f0ff536aa13affafdca6f4d95b031fa3b1856fb722a27e8ee043
```


----

### Keystore

Generate Eth1 keystore.json with SK = 0x01
```
./run-keystore.sh write -k 0x01
```

Generate Eth1 keystore.json with mnemomnic
```
./run-keystore.sh write -k 'word1 ... word24'
```

Generate Eth2 keystore.json with mnemomnic
```
./run-keystore.sh write -t eth2 -k 'word1 ... word24'
```

Sign Metamask style

```
./run-keystore.sh sign -k 0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db test
```

Recover Public Key from Metamask sig

```
./run-keystore.sh recover -k 0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db 0x2c11b15223fa3d9f8320049dc1a576f4c55bd40a3bd3b9760f5cf1a608c8a55a43d0315421a39451e13f23767a1a1f4c2b1d7544fdaf9da5ac94982410e61aa81c test
```

----

### Calls

```
ETH_RPC_URL=http://geth:8545 ./run-evm.sh 0xdAC17F958D2ee523a2206206994597C13D831ec7 "name()(string)"
```

```
ETH_RPC_URL=http://geth:8545 ./run-evm.sh 0xdAC17F958D2ee523a2206206994597C13D831ec7 "balanceOf(address)(uint256)" 0xF977814e90dA44bFA03b6295A0616a897441aceC
```

Encode Function:

```
ETH_RPC_URL=http://geth:8545 ./run-evm.sh encode "balanceOf(address)(uint256)" 0xF977814e90dA44bFA03b6295A0616a897441aceC
```

----
## Certificates

### SSL Certificates Architecture

<img src="doc/CA.jpg" width="900">

