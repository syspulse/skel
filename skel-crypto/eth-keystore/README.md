# eth-keystore 

Ethereum Keystore Utils

## Installation

```
npm install
```

## Read keystore to get PrivetKey (SK)

```
./eth-keystore-read.js <keystore.json> <password>
```

## Create keystore from Private Key (SK)
```
./eth-keystore-write.js <SK> <password> >keystore.json
```

## Libraries and Credits

1. Keystore Format: [https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition](https://github.com/ethereum/wiki/wiki/Web3-Secret-Storage-Definition)