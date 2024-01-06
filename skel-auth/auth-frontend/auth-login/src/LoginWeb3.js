import React, { useContext, useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { BrowserRouter, Route, Routes, Switch } from 'react-router-dom';
import { useLocation } from 'react-router-dom';

import './App.css';

import { baseUrl } from './App';
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
      console.log("sigData=",sigData)
      const sig = await signer.signMessage(sigData);
      const msg64 = btoa(sigData)
      console.log("msg64=",msg64)
      
      const clientId="eaf9642f76195dca7529c0589e6d6259";
      const redirectUri = baseUrl + "/eth/callback";
      const authUri = baseUrl + "/eth/auth" + "?" + 
        "msg=" + msg64 + 
        "&sig=" + sig + 
        "&addr="+ address +
        "&redirect_uri=" + redirectUri + 
        "&client_id=" + clientId
        
      console.log("authUri: ",authUri)
      const rsp = await axios.get(authUri);
      
      console.log("rsp: ",rsp);
      //setLoginStatus(JSON.stringify(rsp.data));
      // setState(JSON.stringify(rsp.data, null, 2));
      setState(
        { ...state, auth: JSON.stringify(rsp.data,null, 2) }
      ); 
      
    } catch (err) {
      console.error(err.message);
    }
  };
  
  function generateSigDataTolerance(address,tolerance = (1000 * 60 * 60 * 24)) {
    const data = "timestamp: "+(Math.trunc(Date.now() / tolerance))+","+"address: "+address
    return data;
  };

  function generateSigData(address) {
    const data = "timestamp: "+Date.now()+","+"address: "+address
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
