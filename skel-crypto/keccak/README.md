# keccak256

1. Reads data from stdin or file
2. Reads in binary of hex string
3. Writes to stdout in binary or hex string


## Examples

Hash text
```
echo -n "message" | keccak
```

Hash of binary data
```
echo -n "0xf1a4" | keccak -x
```

Output binary data
```
echo -n "message" | keccak -b | xxd -p -c 1000
```

## Libraries and Credits

1. tiny-keccak: [https://github.com/debris/tiny-keccak](https://github.com/debris/tiny-keccak)