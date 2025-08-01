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

## ABI

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

## Keystore

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

## EVM


### Function calls

set RPC or pass it with `--eth.rpc.url=`

```
export ETH_RPC_URL=http://geth:8545
```

```
 ./run-evm.sh call 0xdAC17F958D2ee523a2206206994597C13D831ec7 "name()(string)"
```

```
./run-evm.sh call 0xdAC17F958D2ee523a2206206994597C13D831ec7 "balanceOf(address)(uint256)" 0xF977814e90dA44bFA03b6295A0616a897441aceC
```

Complex call to get AAVE pool health which returns tuple:

```
./run-evm.sh call 0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2 "getUserAccountData(address)((uint256,uint256,uint256,uint256,uint256,uint256))" 0xf0bb20865277abd641a307ece5ee04e79073416c
```

Very complex call to AAVE UI Stats: returns tuple with tuple[] inside:

```
export PARAM_1='(address,string,string,uint256,uint256,uint256,uint256,uint256,bool,bool,bool,bool,uint256,uint256,uint256,uint256,uint256,address,address,address,uint256,uint256,uint256,address,uint256,uint256,uint256,uint256,bool,bool,uint256,uint256,uint256,bool,uint256,uint256,uint256,uint256,bool,bool,uint8)'

./run-evm.sh call-async 0x3f78bbd206e4d3c504eb854232eda7e47e9fd8fc "getReservesData(address)($PARAM_1[],(uint256,int256,int256,uint8))" 0x2f39d218133AFaB8F2B819B1066c7E434Ad94E9e
```

### Function Encode

Encoding function call (before it is submitted to the Blockchain)

```
./run-evm.sh encode "balanceOf(address)(uint256)" 0xF977814e90dA44bFA03b6295A0616a897441aceC
```

### ABI Encode

ABI Encoding Parameters. It is used internall by __Function encode__

__NOTE__: Pay attention, that parameters is just a list:

`address,int256` - encoding two parameter
`(address,int256)` - encoding 1 tuple parameter

Encoding 2 params:

```
./run-evm.sh abi-encode "address,int256" 0xF977814e90dA44bFA03b6295A0616a897441aceC 100

0x7bf8e6a9000000000000000000000000f977814e90da44bfa03b6295a0616a897441acec0000000000000000000000000000000000000000000000000000000000000064
```


Encoding Tuple:

```
./run-evm.sh abi-encode "(address,int256)" "(0xF977814e90dA44bFA03b6295A0616a897441aceC,100)"

0x90c97bc20000000000000000000000000000000000000000000000000000000000000020000000000000000000000000f977814e90da44bfa03b6295a0616a897441acec0000000000000000000000000000000000000000000000000000000000000064
```

### ABI Decode

ABI Decode is very useful to decode function call results or Events

Call without result decoding:

```
./run-evm.sh call 0x87870Bca3F3fD6335C3F4ce8392D69350B4fA4E2 "getUserAccountData(address)" 0xf0bb20865277abd641a307ece5ee04e79073416c
```

Now decode result (pay attention to how type parameter is passed):

```
./run-evm.sh abi-decode "(uint256,uint256,uint256,uint256,uint256,uint256)" 0x000000000000000000000000000000000000000000000000034e90c4b32f955c00000000000000000000000000000000000000000000000002fa8632f8c19fc70000000000000000000000000000000000000000000000000018c81d8f2aa0e3000000000000000000000000000000000000000000000000000000000000251c00000000000000000000000000000000000000000000000000000000000024540000000000000000000000000000000000000000000000000ea30f907ee9943c

r = Success((238287004791444828,214631485733445575,6975428722598115,9500,9300,1054703851013772348))
```

## Call Trace

`callTracer`

```
./run-evm.sh call-trace --from 0xfC011860c9E4B840AB97c2c3936611c88fcE3673 0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D "transferFrom(address,address,uint256)" 0xfC011860c9E4B840AB97c2c3936611c88fcE3673 0x0000000000000000000000000000000000000000 1 --tracer=callTracer
```

`prestateTracer` diff mode:

```
./run-evm.sh call-trace --from 0xfC011860c9E4B840AB97c2c3936611c88fcE3673 0xe76C6c83af64e4C60245D8C7dE953DF673a7A33D "transfer(address,uint256)" 0x0000000000000000000000000000000000000001 1 --tracer=prestateTracer --tracerConfig="diffMode=true" --format=json
```

----
## Certificates

### SSL Certificates Architecture

<img src="doc/CA.jpg" width="900">

