import React, { useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { useLocation } from 'react-router-dom';

import './App.css';

import { baseUrl } from './App';

export default function LoginTwitter() {
  
  function serverTwitterLoginUrl() {
    const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";
    const redirectUri=baseUrl + "/callback/twitter";
    const scope="users.read tweet.read";

    const challenge = "challenge-"+ Math.trunc(Date.now() / (1000 * 60 * 60));
    
    const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
    return loginUrl;
  }

  function clientTwitterLoginUrl() {
    const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";

    //const redirectUri = "http://localhost:3000/callback";
    const redirectUri= baseUrl + "/callback/twitter";
    
    const scope="users.read tweet.read";

    const challenge = "challenge";
    console.log("challenge: ",challenge);

    const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
    
    return loginUrl;
  }
  
  return (
    <div>
      <div>
        <button onClick={() => window.open(clientTwitterLoginUrl(),"_self")} > Twitter (Client)</button>
        <span>  </span>
        <button onClick={() => window.open(serverTwitterLoginUrl(),"_self")} > Twitter (Server) </button>
        <span>  </span>
        <a className="App-link" href={serverTwitterLoginUrl()}>Twitter (Server)</a>
      </div>
    </div>
  );

}
