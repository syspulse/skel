#!/usr/bin/env node
const { ethers } = require("ethers");

let sk = "0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db"
let msg = ""

if(process.argv.length > 2) { 
  sk = process.argv[2];
}

if(process.argv.length > 3) { 
  msg = process.argv[3];
}

const w = new ethers.Wallet(sk)
//console.log("address:",w.address)

function generateSigData(address,tolerance = (1000 * 60 * 60 * 24)) {
    const data = "timestamp: "+(Math.trunc(Date.now() / tolerance))+","+"address: "+address
    return data;
};

if(msg == "") msg = generateSigData(w.address)

const sig = w.signMessage(msg)

sig.then( (sig) => {
  console.log(`${w.address} ${sig}`)
})
