import React, { useRef, useEffect, useState } from "react";
import { BrowserRouter, Router, Route, Routes } from 'react-router-dom';

import './App.css';

import LoginStateContext from "./LoginStateContext";
import Login from "./Login";
import LoginTwitterCallback from './LoginTwitterCallback';

// function App() {
//   return (
//     <div className="App">
//       <header className="App-header">  
//       <b>skel-auth</b>
//       </header>
//       <br></br>
//       <Login/>
//     </div>
//   );
// }

function getInitialState() {
  const initial = localStorage.getItem('state')
  return initial ? JSON.parse(initial) : {auth:"",api_res:""}
}

export const baseUrl = "http://localhost:8080/api/v1/auth";

const App = () => {
  const [state, setState] = useState(getInitialState);//useState({auth:"",api_res:""});
  const value = { state, setState };
  const inputRef = useRef(null);

  // useEffect(() => {
  //   setState(localStorage.getItem("state"));
  // }, []);

  useEffect(() => {
    localStorage.setItem("state", JSON.stringify(state));
  }, [state]);


  async function callApi() {
    const jwt = state.auth ? JSON.parse(state.auth).accessToken : ""
    const apiSuffix = inputRef.current.value
    console.log(`===> input: ${apiSuffix}, jwt: ${jwt}`)
    const headers = { 
      'Content-Type': 'application/json',
      'Authorization' : `Bearer ${jwt}`
    };
    const res = await fetch(`${baseUrl}${apiSuffix}`, { headers })
        //.then(response => response.json())
        //.then(data => this.setState({ totalReactPackages: data.total }));
    const data = await res.text();
    console.log(`===> ${data}`)
    try {
      const json = JSON.parse(data)
      setState(
        { ...state, api_res: json }
      ); 
    } catch (error) {
      console.error(error);
      setState(
        { ...state, api_res: data }
      );
    }
  }

  async function logoff() {
    const jwt = state.auth ? JSON.parse(state.auth).accessToken : ""    
    const uid = state.auth ? JSON.parse(state.auth).uid : ""

    console.log(`===> uid: ${uid}, jwt: ${jwt}`)

    const headers = { 
      'Content-Type': 'application/json',
      'Authorization' : `Bearer ${jwt}`
    };
    const res = await fetch(`${baseUrl}/logoff/${uid}`, { method: "POST", headers })        
    const data = await res.text();
    console.log(`===> ${data}`)
    try {
      if(res.status != 200)
        throw `error: ${res.status}`;
      const json = JSON.parse(data)
      setState(
        { ...state, auth: "", api_res: json }
      ); 
    } catch (error) {
      console.error(error);
      setState(
        { ...state, api_res: data }
      );
    }
  }

  return (
    <BrowserRouter>
      <div className="app">
        <b>skel-auth FrontEnd</b>
        <div class="sep1"></div>
        <div><b>ATTENTION:</b>
          <div>Don't forget about Cache (HTTP REDIRECT is cached by browser and reuse the same code !). Hold on "Reload" button and select 'Clear cache' </div>
          <div>Don't forget about CORS </div>
        </div>
        <div class="sep1"></div>
        <input class="input_server" value={baseUrl}></input>
        <div class="sep1"></div>
        <div>
          <LoginStateContext.Provider value={value}>
            <Routes>            
              <Route path="/" element={<Login/>} />
              <Route path="/callback" element={<LoginTwitterCallback/>} />
            </Routes>
          </LoginStateContext.Provider>
        </div>
        <div class="sep1"></div>
        <button onClick={logoff}>Log-off</button>
        <div class="sep2"></div>
        <div><pre class="pre_auth">{state.auth}</pre></div>
        <div>
          <span>api:</span> 
          <input class="input_api" ref={inputRef} defaultValue={state.auth ? "/user/" + JSON.parse(state.auth).uid : "/user"}></input>
          <button onClick={callApi}>Run...</button>
        </div>
        <div><pre class="pre_response">{JSON.stringify(state.api_res)}</pre></div>
      </div>
      
      
    </BrowserRouter>
  );
};

export default App;
