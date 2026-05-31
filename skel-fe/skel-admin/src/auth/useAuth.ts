import { useContext } from 'react';
import { AuthContext, AuthState } from './AuthContext';

export function useAuth(): AuthState {
  return useContext(AuthContext);
}
