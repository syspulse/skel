#!/usr/bin/env node
// usage example:
// ./eth-func-hash.js 'balanceOf(address)'

const { ethers } = require("ethers");
const fs = require('fs')

let func = (process.argv.length<3) ? "totalSupply()" : process.argv[2];
let hash = ethers.utils.keccak256(ethers.utils.toUtf8Bytes(func))
let funcHash = hash.substring(0,10)
console.log(func,funcHash);

