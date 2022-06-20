import React, { useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { useLocation } from 'react-router-dom';

import './App.css';

const baseUrl = "http://localhost:8080/api/v1/auth";


export default function LoginTwitter() {
  const [loginStatus, setLoginStatus] = useState("not authenticated");
  const [error, setError] = useState();
  
  function serverTwitterLogin() {
    const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";
    const redirectUri=baseUrl + "/callback/twitter";
    const scope="users.read tweet.read";

    const challenge = "challenge-"+ Math.trunc(Date.now() / (1000 * 60 * 60));

    const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
    return loginUrl;
  }

  function clientTwitterLogin() {
    const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";
    const redirectUri = "http://localhost:3000/callback";
    const scope="users.read tweet.read";

    const challenge = "challenge";

    const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
    
    return loginUrl;
  }
  
  return (
    <div>
      <div>
        {/* <a className="App-link" href={clientTwitterLogin()}>Twitter Login (Client)</a> */}
        <button onClick={() => window.open(clientTwitterLogin(),"_self")} > Twitter (Client)</button>
        <span>  </span>
        <button onClick={() => window.open(serverTwitterLogin(),"_self")} > Twitter (Server) </button>
        <span>  </span>
        <a className="App-link" href={serverTwitterLogin()}>Twitter Login (Server)</a>
      </div>
      <br/>
      <div>{loginStatus}</div>
    </div>
  );

}
