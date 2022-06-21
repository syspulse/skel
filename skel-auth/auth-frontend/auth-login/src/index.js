import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
// import reportWebVitals from './reportWebVitals';
import { BrowserRouter, Route, Routes, Switch } from 'react-router-dom';

import Login from "./Login";
import LoginTwitterCallback from "./LoginTwitterCallback";

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
  // <BrowserRouter>
  //   <Routes>
  //       <Route path="/" element={<Login/>} />             
  //       <Route path="/callback" element={<LoginTwitterCallback/>} />
  //   </Routes>
  // </BrowserRouter>
);
