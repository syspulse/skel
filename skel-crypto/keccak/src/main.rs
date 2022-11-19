extern crate clap;
extern crate tiny_keccak;
extern crate hex;

use std::io;
use std::io::Write;
use tiny_keccak::Sha3;
use tiny_keccak::Keccak;
use tiny_keccak::Hasher;
use clap::{App, Arg};
use std::option::{Option};
// use hex::{FromHex, ToHex};

fn reading() -> Option<String> { 
    let mut buffer:String = String::with_capacity(1024);
    let _ = io::stdin().read_line(&mut buffer);
    Some(buffer)
}

fn main() {
    let matches = App::new("keccak")
        .version("0.0.1")
        .arg(Arg::with_name("DATA").index(1).required(false))
        .arg(Arg::with_name("x").short("x").help("hex input"))
        .arg(Arg::with_name("b").short("b").help("binary output"))
        .arg(Arg::with_name("e").short("e").help("Ethereum signature style"))
        .get_matches();
    
    let data0 = matches
        .value_of("DATA")
        .map(|s| {String::from(s)})
        .or_else(reading)
        .map(|s| {
            if matches.is_present("x") {
                let ss:String = {
                    if s.contains("0x") {
                        s.to_string().chars().skip(2).take(s.len()-2).collect()
                    } else {
                        s
                    }
                };
                unsafe { String::from_utf8_unchecked(hex::decode(ss).expect("Decoding failed")) }
            } else {
                s
            }
        })
        .unwrap();

    let data = 
        if matches.is_present("e") {
            let size = data0.len();
            let prehash = format!("\u{0019}Ethereum Signed Message:\n{}{}",size,data0);
            prehash
        } else {
            data0
        };

    let mut hash_keccak = Keccak::v256();
    let mut hash_sha3 = Sha3::v256();
        
    let mut output_keccak:[u8; 32] = [0;32];
    let mut output_sha3 = [0u8; 32];

    hash_keccak.update(data.as_bytes());
    hash_keccak.finalize(&mut output_keccak);

    hash_sha3.update(data.as_bytes());
    hash_sha3.finalize(&mut output_sha3);

    if matches.is_present("b") {
        let _ = std::io::stdout().write_all(&output_keccak);
        let _ = std::io::stdout().flush();
    }
    else {
        println!("{}", hex::encode(output_keccak));
    }
    // println!("keccak({:?}) = {}", data, hex::encode(output_keccak));
    // println!("sha3({:?}) = {}", data, hex::encode(output_sha3));
}

