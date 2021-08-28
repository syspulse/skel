extern crate clap;
extern crate tiny_keccak;
extern crate hex;

use tiny_keccak::Sha3;
use tiny_keccak::Keccak;
use tiny_keccak::Hasher;
use clap::{App, Arg};
// use hex::{FromHex, ToHex};

fn main() {
    let matches = App::new("keccak")
        .arg(Arg::with_name("DATA").index(1).required(true))
        .get_matches();
    
    let data = matches
        .value_of("DATA")
        .unwrap_or("");

    let mut hash_keccak = Keccak::v256();
    let mut hash_sha3 = Sha3::v256();
        
    let mut output_keccak:[u8; 32] = [0;32];
    let mut output_sha3 = [0u8; 32];

    hash_keccak.update(data.as_bytes());
    hash_keccak.finalize(&mut output_keccak);

    hash_sha3.update(data.as_bytes());
    hash_sha3.finalize(&mut output_sha3);

    println!("{}", hex::encode(output_keccak));
    // println!("keccak({:?}) = {}", data, hex::encode(output_keccak));
    // println!("sha3({:?}) = {}", data, hex::encode(output_sha3));
}

