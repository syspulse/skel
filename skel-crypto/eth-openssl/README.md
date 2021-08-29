# eth-key 

OpenSSL based utils for ECDSA keys

1. Keys generation (and address)
2. Signing
3. Signature verification

__NOTE__: Beware that ECDSA signatures are not deterministic (different sig is generated for the same SK and file!).

See [https://github.com/openssl/openssl/pull/9223](https://github.com/openssl/openssl/pull/9223)

## Examples

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

1. OpenSSL ECDSA: [https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations](https://wiki.openssl.org/index.php/Command_Line_Elliptic_Curve_Operations)
2. Deterministic Signatures: [https://datatracker.ietf.org/doc/html/rfc6979](https://datatracker.ietf.org/doc/html/rfc6979)