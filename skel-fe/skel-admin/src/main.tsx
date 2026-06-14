import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import { AppProvider } from './theme/AppContext';
import { ThemeProvider } from './theme/ThemeContext';
import { NotificationProvider } from './notifications/NotificationContext';
import { DispatcherProvider } from './modules/dispatcher/DispatcherContext';
import { dispatcher } from './modules/dispatcher/Dispatcher';
import { explainSysHandler } from './modules/dispatcher/systems/ExplainSys';
import { dashSysHandler } from './modules/dispatcher/systems/DashSys';
import { PageSizeProvider } from './settings/PageSizeContext';
import { TimestampFormatProvider } from './settings/TimestampFormatContext';
import { WorkflowGridProvider } from './settings/WorkflowGridContext';
import './i18n';
import './styles.css';

// Register module system handlers before any providers mount
dispatcher.register('ExplainSys', explainSysHandler);
dispatcher.register('DashSys', dashSysHandler);
// NotificationSys is registered inside NotificationProvider (needs push callback)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ThemeProvider>
      <AppProvider>
        <NotificationProvider>
          <DispatcherProvider>
            <PageSizeProvider>
              <TimestampFormatProvider>
                <WorkflowGridProvider>
                  <AuthProvider>
                    <App />
                  </AuthProvider>
                </WorkflowGridProvider>
              </TimestampFormatProvider>
            </PageSizeProvider>
          </DispatcherProvider>
        </NotificationProvider>
      </AppProvider>
    </ThemeProvider>
  </React.StrictMode>,
);
