import React, { useState } from "react";
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

const App = () => {
  const [state, setState] = useState("{}");
  const value = { state, setState };

  return (
    <BrowserRouter>
      <div className="app">
        <b>skel-auth</b>
        <div>
          <LoginStateContext.Provider value={value}>
            <Routes>            
              <Route path="/" element={<Login/>} />
              <Route path="/callback" element={<LoginTwitterCallback/>} />
            </Routes>
          </LoginStateContext.Provider>
        </div>
        <div>{state}</div>
      </div>
      
      
    </BrowserRouter>
  );
};

export default App;
