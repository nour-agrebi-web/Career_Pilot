import { createContext, ReactNode, useContext, useEffect, useState } from 'react';

import * as api from './api';
import { getRefreshToken } from './tokens';

type AuthState = {
  user: api.CurrentUser | null;
  loading: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  signUp: (email: string, password: string, language: string, consent: boolean) => Promise<void>;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<api.CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    api.setSessionExpiredHandler(() => setUser(null));
    // Au démarrage : si un jeton existe, on restaure la session sans redemander les identifiants.
    (async () => {
      try {
        if (await getRefreshToken()) setUser(await api.fetchMe());
      } catch {
        setUser(null);
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  async function signIn(email: string, password: string) {
    await api.login(email, password);
    setUser(await api.fetchMe());
  }

  async function signUp(email: string, password: string, language: string, consent: boolean) {
    await api.register(email, password, language, consent);
    await signIn(email, password);
  }

  async function signOut() {
    await api.logout();
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, loading, signIn, signUp, signOut }}>{children}</AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth doit être utilisé dans <AuthProvider>');
  return ctx;
}
