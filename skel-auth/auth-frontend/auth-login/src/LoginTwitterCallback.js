import React, { useState } from "react";
import axios from "axios";

import { useLocation } from 'react-router-dom';

import './App.css';

const baseUrl = "http://localhost:8080/api/v1/auth";

export default function LoginTwitterCallback() {
  const [loginStatus, setLoginStatus] = useState("not authenticated");

  const { search } = useLocation();
  const match = search.match(/code=(.*)/);
  const code = match?.[1];
  
  function clientTwitterLoginCallback(code) {
    console.log("code: ",code);
  }

  function clientTwitterLoginCallback(code) {
    console.log("auth_code: ",code);

    const challenge = "challenge";
    const redirectUri = "http://localhost:3000/callback";
    const tokenUrl = `http://localhost:8080/api/v1/auth/token/twitter?code=${code}&redirect_uri=${redirectUri}&challenge=${challenge}`
    console.log("tokenUrl",tokenUrl);
    
    const serverRsp = axios.get(tokenUrl);
    serverRsp.then( (rsp) => {
      console.log("server: ",rsp);
      setLoginStatus(JSON.stringify(rsp.data));
    })
    
  }
  
  return (
    <>
      {clientTwitterLoginCallback(code)}
    </>
      
  );
}
