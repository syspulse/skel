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

## Cross-compile build for RP3 (arm-linux-gnueabihf) and RP4 (aarch64-linux-gnu)

```
# RP4
rustup target add aarch64-unknown-linux-gnu
sudo apt install gcc-aarch64-linux-gnu
# RP3
rustup target add armv7-unknown-linux-gnueabihf
sudo apt install gcc-multilib-arm-linux-gnueabihf

mkdir .cargo
echo '[target.aarch64-unknown-linux-gnu]' >>.cargo/config
echo 'linker = "aarch64-linux-gnu-gcc"' >>.cargo/config
echo '[target.armv7-unknown-linux-gnueabihf]' >>.cargo/config
echo 'linker = "arm-linux-gnueabihf-gcc"' >>.cargo/config

cargo build --release --target=aarch64-unknown-linux-gnu
cargo build --release --target=armv7-unknown-linux-gnueabihf
```

## Libraries and Credits

1. tiny-keccak: [https://github.com/debris/tiny-keccak](https://github.com/debris/tiny-keccak)