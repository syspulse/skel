### Ethereum Streaming


Run without logs against dump file
```
cat tx-1734.log | ./run-app.sh --class.name io.syspulse.skel.stream.eth.StreamEthTx --script @script-1.js
```

Stream from ethereum-etl
```
ethereumetl stream -e transaction --start-block 14747950 --provider-uri $ETH_RPC | ./run-app.sh --class.name io.syspulse.skel.stream.eth.StreamEthTx --script @script-1.js
```
