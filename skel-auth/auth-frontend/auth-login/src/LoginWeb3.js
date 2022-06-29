import React, { useContext, useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { BrowserRouter, Route, Routes, Switch } from 'react-router-dom';
import { useLocation } from 'react-router-dom';

import './App.css';

import { baseUrl } from './Login';
import LoginStateContext from "./LoginStateContext";

export default function LoginWeb3() {
  const { state, setState } = useContext(LoginStateContext);

  async function web3Login() {
    try {
      if (!window.ethereum)
        throw new Error("No crypto wallet found. Please install it.");
  
      let currentAccount = null;
      window.ethereum
        .request({ method: "eth_accounts" })
        .then(handleAccountsChanged)
        .catch((err) => {
          // Some unexpected error.
          // For backwards compatibility reasons, if no accounts are available,
          // eth_accounts will return an empty array.
          console.error(err);
        });
      window.ethereum.on("accountsChanged", handleAccountsChanged);
  
      // For now, 'eth_accounts' will continue to always return an array
      function handleAccountsChanged(accounts) {
        if (accounts.length === 0) {
          // MetaMask is locked or the user has not connected any accounts
          console.log("Please connect to MetaMask.");
        } else if (accounts[0] !== currentAccount) {
          currentAccount = accounts[0];
          // Do any other work!
        }
      }
  
      await window.ethereum.send("eth_requestAccounts");
      const provider = new ethers.providers.Web3Provider(window.ethereum);
      const signer = provider.getSigner();
      const address = (await signer.getAddress()).toLowerCase();
  
      const sigData = generateSigData(address);
      const sig = await signer.signMessage(sigData);
      
      const redirectUri = baseUrl + "/eth/callback";
      const authUri = baseUrl + "/eth/auth" + "?sig=" + sig + "&addr="+ address +"&redirect_uri=" + redirectUri
      console.log("authUri: ",authUri)
      const rsp = await axios.get(authUri);
      
      console.log("rsp: ",rsp);
      //setLoginStatus(JSON.stringify(rsp.data));
      setState(JSON.stringify(rsp.data));
      
    } catch (err) {
      console.error(err.message);
    }
  };
  
  function generateSigData(address,tolerance = 15000) {
    let data = "timestamp: "+(Math.trunc(Date.now() / tolerance) + 1 )+","+"address: "+address
    return data;
  };
  
  return (
    <div>            
      <div>
        <button onClick={() => web3Login()} >
          Web3 (Metamask)
        </button>
        <span>  </span>
        {/* <a className="App-link" href={web3Login()}>Metamask Login</a> */}
      </div>
    </div>
  );

}
