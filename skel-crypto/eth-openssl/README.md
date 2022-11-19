# eth-key 

OpenSSL based utils for ECDSA keys

1. Keys generation (and address)
2. Signing
3. Signature verification

__NOTES__: 
1. Beware that ECDSA signatures are not deterministic (different sig is generated for the same SK and file!).
2. Signatures and Eth address require [../keccak](../keccak) utility

See [https://github.com/openssl/openssl/pull/9223](https://github.com/openssl/openssl/pull/9223)

## Examples

Generate Keys with Ethereum address
```
./key-generate-address.sh
```

Generate PEM file with kes (sk.pem and pk.pem). The format is pk8 to be compatible with JDK
```
./key-generate-keyfiles.sh
```

Sign __file__:
```
SK_FILE=sk.pem ./key-sign <file>
```

Verify signature (in __signature-file__):
```
PK_FILE=pk.pem ./key-verify <file> <signature-file>
```

Sign data and get __r:s__ 
```
echo -n "message" | ./sig-data-sign.sh | paste - - -|awk '{print $13,$20}' | awk -F':' '{print "0"substr($2,1,length($2)-1)":""0"$3}'
```


## Libraries and Credits

1. OpenSSL ECDSA: [https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations](https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations)
2. Deterministic Signatures: [https://datatracker.ietf.org/doc/html/rfc6979](https://datatracker.ietf.org/doc/html/rfc6979)