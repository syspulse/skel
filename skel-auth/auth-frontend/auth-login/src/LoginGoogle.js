import React, { useContext, useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { useLocation } from 'react-router-dom';

import './App.css';

import { baseUrl } from './Login';
import LoginStateContext from "./LoginStateContext";

export default function LoginGoogle() {
  const { state, setState } = useContext(LoginStateContext);

  const onGoogleClient = async (rsp) => {
    console.log(rsp);
    const code = rsp.code;
    // need to override to client redirect to successfully authorize
    const redirectUri = "http://localhost:3000"
    // const tokenUrl = `http://api.hacken.cloud/api/v1/auth/token/google?code=${code}&redirect_uri=${redirectUri}`
    //const tokenUrl = `http://localhost:8080/api/v1/auth/token/google?code=${code}&redirect_uri=${redirectUri}`
    const tokenUrl = `${baseUrl}/token/google?code=${code}&redirect_uri=${redirectUri}`
    console.log(tokenUrl);
    
    const serverRsp = await axios.get(tokenUrl);
    console.log("serverRsp: ",serverRsp);
    setState(JSON.stringify(serverRsp.data,null, 2));
  };
  
  const onGoogleServer = rsp => {
    console.log(rsp);
    setState(JSON.stringify(rsp,null, 2));
  };
  
  
  function serverGoogleLoginUrl() {
    const clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com";
    const redirectUri=baseUrl + "/callback/google";
    const scope="openid profile email";
  
    const loginUrl = `https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${clientId}&scope=${scope}&redirect_uri=${redirectUri}`
    return loginUrl;
  }
  
  
  return (
    <div>
      <div>
        <GoogleLogin
          clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com"
          
          uxMode={"redirect"}
          // IT IS IGNORED BY COMPONENT !
          redirectUri={"http://localhost:3000"}
          responseType={'code'}
          
          scope={"openid profile email"}
          fetchBasicProfile={false}
          
          buttonText="Login (Client)"
          onSuccess={onGoogleClient}
          onFailure={onGoogleClient}
          cookiePolicy={"single_host_origin"}
        />
        <span>  </span>
        <GoogleLogin
          clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com"
          
          uxMode={"popup"}
          
          scope={"openid profile email"}
          fetchBasicProfile={false}
          
          buttonText="Login (Server)"
          onSuccess={onGoogleServer}
          onFailure={onGoogleServer}
          cookiePolicy={"single_host_origin"}
        />
        <span>  </span>
        <button onClick={() => window.open(serverGoogleLoginUrl(),"_self")} >
        Google (Server)
        </button>
        <span>  </span>
        <a className="App-link" href={serverGoogleLoginUrl()}>Google (Server)</a>
      </div>
    </div>
  );

}
