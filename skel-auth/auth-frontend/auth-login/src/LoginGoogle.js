import React, { useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { useLocation } from 'react-router-dom';

import './App.css';

const baseUrl = "http://localhost:8080/api/v1/auth";


export default function LoginGoogle() {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [url, setUrl] = useState("");
  const [loginStatus, setLoginStatus] = useState("not authenticated");
  const [error, setError] = useState();

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
  
  function serverGoogleLogin() {
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
        <button onClick={() => window.open(serverGoogleLogin(),"_self")} >
        Google (Server)
        </button>
        <span>  </span>
        <a className="App-link" href={serverGoogleLogin()}>Google Login</a>
      </div>
      <br/>
      <div>{loginStatus}</div>
      
    </div>
  );

}
