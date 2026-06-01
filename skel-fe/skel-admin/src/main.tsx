import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { AppProvider } from './theme/AppContext';
import { ThemeProvider } from './theme/ThemeContext';
import { NotificationProvider } from './notifications/NotificationContext';
import { PageSizeProvider } from './settings/PageSizeContext';
import './i18n';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AppProvider>
        <NotificationProvider>
          <PageSizeProvider>
            <AuthProvider>
              <App />
            </AuthProvider>
          </PageSizeProvider>
        </NotificationProvider>
      </AppProvider>
    </ThemeProvider>
  </React.StrictMode>,
);
