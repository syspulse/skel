import React, { useContext, useState } from "react";
import axios from "axios";

import { useLocation } from 'react-router-dom';
import { Navigate, Outlet } from 'react-router-dom';

import { baseUrl } from './Login';
import LoginStateContext from "./LoginStateContext";

var _code = "";

export default function LoginTwitterCallback() {
  
  const { state, setState } = useContext(LoginStateContext);

  const { search } = useLocation();
  const match = search.match(/code=(.*)/);
  const code = match?.[1];
  
  function clientTwitterLoginCallback(code) {
    console.log("_code: ",_code);
    console.log("auth_code: ",code);
    if(_code != code) {
      _code = code;

      const challenge = "challenge";
      const redirectUri = "http://localhost:3000/callback";
      const tokenUrl = `${baseUrl}/token/twitter?code=${code}&redirect_uri=${redirectUri}&challenge=${challenge}`
      console.log("tokenUrl",tokenUrl);
      
      const serverRsp = axios.get(tokenUrl);
      serverRsp.then( (rsp) => {
        console.log("server: ",rsp);
        console.log("====> LoginStateContext",setState);
        setState(JSON.stringify(rsp.data, null,2));
      })
    }

    return <Navigate to="/" />
  }
  
  return (
    <>
      {clientTwitterLoginCallback(code)}
    </>
      
  );
}
