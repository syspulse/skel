import React, { useState } from "react";
import { ethers } from "ethers";
import axios from "axios";

import { GoogleLogin, GoogleLogout } from "react-google-login";
import { BrowserRouter, Route, Routes, Switch } from 'react-router-dom';
import { useLocation } from 'react-router-dom';

import LoginWeb3 from "./LoginWeb3";
import LoginGoogle from "./LoginGoogle";
import LoginTwitter from "./LoginTwitter";
import LoginTwitterCallback from "./LoginTwitterCallback";

import './App.css';


export default function Login() {
  
  return (
      <>
        <LoginTwitter/><br/>
        <LoginGoogle/><br/>
        <LoginWeb3/><br/>
      </>
  );

}
