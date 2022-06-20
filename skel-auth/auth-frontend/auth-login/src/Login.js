import React, { useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { BrowserRouter, Route, Switch } from 'react-router-dom';

import './App.css';

const baseUrl = "http://localhost:8080/api/v1/auth";


export default function Login() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [url, setUrl] = useState("");
  const [loginStatus, setLoginStatus] = useState("not authenticated");
  const [error, setError] = useState();

  const onTwitterServer = (err, data) => {
    console.log(err, data);

  };
  
  const onGoogleServer = async (rsp) => {
    console.log(rsp);
    const code = rsp.code;
    const redirectUri = "http://localhost:3000"
    const tokenUrl = `http://localhost:8080/api/v1/auth/token/google?code=${code}&redirect_uri=${redirectUri}`
    console.log(tokenUrl);
    
    //setLoginStatus(JSON.stringify(rsp));
    const serverRsp = await axios.get(tokenUrl);
    console.log("server: ",serverRsp);
    setLoginStatus(JSON.stringify(serverRsp.data));
  };
  
  const onGoogleClient = rsp => {
    console.log(rsp);
    // setName(rsp.profileObj.name);
    // setEmail(rsp.profileObj.email);
    // setUrl(rsp.profileObj.imageUrl);
    
    setLoginStatus(JSON.stringify(rsp));
  };
  // const onGoogleLogout = () => {
  //   console.log("logout");
  //   setLoginStatus(false);
  // };
  
  function getGoogleLogin() {
    const clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com";
    const redirectUri=baseUrl + "/callback/google";
    const scope="openid profile email";
  
    const loginUrl = `https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${clientId}&scope=${scope}&redirect_uri=${redirectUri}`
    return loginUrl;
  }
  
  function getTwitterLogin() {
    const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";
    const redirectUri=baseUrl + "/callback/twitter";
    const scope="users.read tweet.read";

    const challenge = "challenge-"+ Math.trunc(Date.now() / (1000 * 60 * 60));

    const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
    return loginUrl;
  }
  
  async function web3Login({setLoginStatus}) {
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
      setLoginStatus(JSON.stringify(rsp.data));
      
    } catch (err) {
      console.error(err.message);
    }
  };
  
  function generateSigData(address,tolerance = 15000) {
    let data = "timestamp: "+Math.trunc(Date.now() / tolerance)+","+"address: "+address
    return data;
  };
  
  return (
    <div>
      <BrowserRouter>
        <Switch>
          <Route path="/">

            <div>
              <GoogleLogin
                clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com"
                
                uxMode={"redirect"}
                // IT IS IGNORED BY COMPONENT !
                redirectUri={"http://localhost:3000"}
                responseType={'code'}
                
                scope={"openid profile email"}
                fetchBasicProfile={false}
                
                buttonText="Login (Server)"
                onSuccess={onGoogleServer}
                onFailure={onGoogleServer}
                cookiePolicy={"single_host_origin"}
              />
              <span>  </span>
              <GoogleLogin
                clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com"
                
                uxMode={"popup"}
                
                scope={"openid profile email"}
                fetchBasicProfile={false}
                
                buttonText="Login Client"
                onSuccess={onGoogleClient}
                onFailure={onGoogleClient}
                cookiePolicy={"single_host_origin"}
              />
              <span>  </span>
              <button onClick={() => window.open(getGoogleLogin(),"_self")} >
              Google
              </button>
              <span>  </span>
              <a className="App-link" href={getGoogleLogin()}>Google Login</a>
            </div>
            <br/>
            <div>
              
              <span>  </span>
              <button onClick={() => window.open(getTwitterLogin(),"_self")} >
              Twitter
              </button>
              <span>  </span>
              <a className="App-link" href={getTwitterLogin()}>Twitter Login</a>
            </div>
            <br/>
            <div>
              <button onClick={() => web3Login({setLoginStatus})} >
                Web3
              </button>
              <span>  </span>
              {/* <a className="App-link" href={web3Login()}>Metamask Login</a> */}
            </div>
            <br/>
            <div>{loginStatus}</div>

          </Route>
        </Switch>
      </BrowserRouter>
      
    </div>
  );

}
