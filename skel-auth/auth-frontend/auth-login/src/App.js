import logo from './logo.svg';
import './App.css';

import Login from "./Login";

// import { ethers } from "ethers";
// import axios from "axios";

// const baseUrl = "http://localhost:8080/api/v1/auth";
// const redirectUri = baseUrl + "/eth/callback";

// function getGoogleLogin() {
//   const clientId="1084039747276-3na59kcrc8ab5k65louvg5jv8ulnmv54.apps.googleusercontent.com";
//   const redirectUri=baseUrl + "/callback/google";
//   const scope="openid profile email";

//   const loginUrl = `https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${clientId}&scope=${scope}&redirect_uri=${redirectUri}`
//   return loginUrl;
// }

// function getTwitterLogin() {
//   const clientId="VExtd2FtdGlBYU5NbWdIemNjWFM6MTpjaQ";
//   const redirectUri=baseUrl + "/callback/twitter";
//   const scope="users.read tweet.read";
//   const challenge = "challenge";
//   const loginUrl = `https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=${scope}&state=state&code_challenge=${challenge}&code_challenge_method=plain`;
//   return loginUrl;
// }

// async function web3Login() {
//   try {
//     if (!window.ethereum)
//       throw new Error("No crypto wallet found. Please install it.");

//     let currentAccount = null;
//     window.ethereum
//       .request({ method: "eth_accounts" })
//       .then(handleAccountsChanged)
//       .catch((err) => {
//         // Some unexpected error.
//         // For backwards compatibility reasons, if no accounts are available,
//         // eth_accounts will return an empty array.
//         console.error(err);
//       });
//     window.ethereum.on("accountsChanged", handleAccountsChanged);

//     // For now, 'eth_accounts' will continue to always return an array
//     function handleAccountsChanged(accounts) {
//       if (accounts.length === 0) {
//         // MetaMask is locked or the user has not connected any accounts
//         console.log("Please connect to MetaMask.");
//       } else if (accounts[0] !== currentAccount) {
//         currentAccount = accounts[0];
//         // Do any other work!
//       }
//     }

//     await window.ethereum.send("eth_requestAccounts");
//     const provider = new ethers.providers.Web3Provider(window.ethereum);
//     const signer = provider.getSigner();
//     const address = (await signer.getAddress()).toLowerCase();

//     const sigData = generateSigData(address);
//     const sig = await signer.signMessage(sigData);
    

//     const authUri = baseUrl + "/eth/auth" + "?sig=" + sig + "&addr="+ address +"&redirect_uri=" + redirectUri
//     console.log("authUri: ",authUri)
//     const { rsp } = await axios.get(authUri);
    
//     return rsp;
    
//   } catch (err) {
//     console.error(err.message);
//   }
// };

// function generateSigData(address,tolerance = 15000) {
//   let data = "timestamp: "+Math.trunc(Date.now() / tolerance)+","+"address: "+address
//   return data;
// };


// function App() {
//   return (
//     <div className="App">
//       <header className="App-header">  
//       </header>
//       <div>
//         <button onClick={() => window.open(getGoogleLogin(),"_self")} >
//         Google
//         </button>
//         <span>  </span>
//         <a className="App-link" href={getGoogleLogin()}>Google Login</a>
//       </div>
//       <br/>
//       <div>
//         <button onClick={() => window.open(getTwitterLogin(),"_self")} >
//         Twitter
//         </button>
//         <span>  </span>
//         <a className="App-link" href={getTwitterLogin()}>Twitter Login</a>
//       </div>
//       <br/>
//       <div>
//         <button onClick={() => web3Login()} >
//           Web3
//         </button>
//         <span>  </span>
//         {/* <a className="App-link" href={web3Login()}>Metamask Login</a> */}
//       </div>
//     </div>
//   );
// }

function App() {
  return (
    <div className="App">
      <header className="App-header">  
      </header>
      <Login/>
    </div>
  );
}

export default App;
