import React, { useEffect, useRef, useState } from 'react';
import './TopPanel.css';
import { GoogleLogin } from '@react-oauth/google';
import { login, logout, isKeycloakLoggedIn, initKeycloak } from '../keycloak';

interface TopMenuProps {
  onLogin: () => void;
}

const TopMenu: React.FC<TopMenuProps> = ({ onLogin }) => {
  const [isTokenValid, setIsTokenValid] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  const toggleDropdown = (event: React.MouseEvent) => {
    event.stopPropagation(); // Prevent event bubbling
    setDropdownOpen(!dropdownOpen);
  };

  useEffect(() => {
    initKeycloak().then(() => {
      setIsTokenValid(isKeycloakLoggedIn());
    });
  }, []);

  const handleJwtLogin = () => {
    const token = prompt('JWT token:');
    if (token) {
      localStorage.setItem('jwtToken', token);
      onLogin();
      setDropdownOpen(false);
    }

    askMe();
  };

  const handleGoogleLoginSuccess = (userInfo: any) => {
    console.log(userInfo);
    // Handle successful login (e.g., store user info, update state)
  };

  const handleKeycloakAuth = () => {
    if (isKeycloakLoggedIn()) {
      logout();
      setIsTokenValid(false);
    } else {
      login();
    }
  };

  function isLoggedIn() {
    return isTokenValid;
  }

  async function askMe() {
    const jwtToken = localStorage.getItem('jwtToken') || '';

    if(isLoggedIn()) {
      try {
        const response = await fetch('https://api.extractor.dev.hacken.cloud/api/v1/user/me', {
          method: 'GET',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${jwtToken}`
          },
          // body: JSON.stringify(request),
          // mode: 'no-cors'
        });

        if (response.ok) {
          const data = await response.json();
          setIsTokenValid(true);

        } else {
          console.error('Failed to refresh');
          setIsTokenValid(false);
        }
      } catch (error) {
        console.error('Error refreshing:', error);
        setIsTokenValid(false);
      }
    } else {
      console.log('Not logged in');
      setIsTokenValid(false);
    }
  }

  useEffect(() => {
    const refreshToken = async () => {
      const refreshToken = localStorage.getItem('refreshToken') || '';

      try {
        const formData = new FormData();
        formData.append('grant_type', 'refresh_token');
        formData.append('refresh_token', refreshToken);
        formData.append('client_id', 'extractor-public');

        const response = await fetch('https://auth.dev.extractor.live/realms/hacken/protocol/openid-connect/token', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded'
          },
          body: formData,
          mode: 'no-cors'
        });

        if (response.ok) {
          const data = await response.json();
          localStorage.setItem('jwtToken', data.access_token);
          localStorage.setItem('refreshToken', data.refresh_token);

          const expire = data.expires_in * 1000;
          setIsTokenValid(true);

        } else {
          console.error('Failed to refresh token');
          setIsTokenValid(false);
        }
      } catch (error) {
        console.error('Error refreshing token:', error);
        setIsTokenValid(false);
      }
    };

    const askMeRefresh = () => {
      askMe()
    };

    // Refresh token every 15 minutes (900000 milliseconds)
    const intervalId = setInterval(askMeRefresh, 60000);

    // Initial token refresh
    askMeRefresh();

    // Cleanup interval on component unmount
    return () => clearInterval(intervalId);
  }, []);

  const handleClickOutside = (event: MouseEvent) => {
    if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
      setDropdownOpen(false);
    }
  };

  useEffect(() => {
    // Add event listener for clicks outside the dropdown
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('click', handleClickOutside); // Add click event listener
    return () => {
      // Cleanup the event listener on component unmount
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('click', handleClickOutside); // Cleanup click event listener
    };
  }, []);


  return (
    <div className="top-menu">
      <div className="user-profile">
        <div className={`user-icon ${isTokenValid ? 'valid' : ''}`} onClick={toggleDropdown}>
          U
        </div>
        {dropdownOpen && (
          <div className="dropdown-menu" ref={dropdownRef}>
            <button className="dropdown-item" onClick={handleJwtLogin}>Login</button>
            {/* <GoogleLogin
              onSuccess={credentialResponse => {
                handleGoogleLoginSuccess(credentialResponse);
              }}
              onError={() => {
                console.log('Login Failed');
              }}
            /> */}

            <button className="dropdown-item" onClick={handleKeycloakAuth}>
              {isKeycloakLoggedIn() ? 'Logout (Keycloak)' : 'Login (Keycloak)'}
            </button>              
          
            <hr />
            <button className="dropdown-item">Settings</button>
            <button className="dropdown-item">Help</button>
          </div>
        )}
      </div>
      {/* <div className={`user-icon ${isTokenValid ? 'valid' : ''}`}>👤</div> */}
    </div>
  );
};

export default TopMenu;
