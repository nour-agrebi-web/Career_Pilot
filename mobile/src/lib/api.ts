import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';

import { clearTokens, getAccessToken, getRefreshToken, saveTokens } from './tokens';

// Émulateur Android : 10.0.2.2 = le PC. Téléphone réel : mettre l'IP du PC dans .env (EXPO_PUBLIC_API_URL).
export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8000';

export type TokenPair = { access_token: string; refresh_token: string; token_type: string };
export type CurrentUser = { id: string; email: string; language: string; role: 'candidate' | 'admin' };

export const api = axios.create({ baseURL: API_URL, timeout: 15000 });

let onSessionExpired: () => void = () => {};
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

api.interceptors.request.use(async (config) => {
  const token = await getAccessToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// Un seul refresh à la fois, même si plusieurs requêtes reçoivent un 401 en même temps.
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = await getRefreshToken();
  if (!refreshToken) return null;
  try {
    const { data } = await axios.post<TokenPair>(`${API_URL}/auth/refresh`, { refresh_token: refreshToken });
    await saveTokens(data.access_token, data.refresh_token);
    return data.access_token;
  } catch {
    await clearTokens();
    return null;
  }
}

// US-1.2.2 : JWT expiré → nouveau JWT sans action de l'utilisateur ; refresh invalide → écran de connexion.
api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const original = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
  if (error.response?.status !== 401 || !original || original._retry || original.url?.startsWith('/auth/')) {
    throw error;
  }
  original._retry = true;
  refreshing ??= refreshAccessToken().finally(() => {
    refreshing = null;
  });
  const token = await refreshing;
  if (!token) {
    onSessionExpired();
    throw error;
  }
  original.headers.Authorization = `Bearer ${token}`;
  return api(original);
});

export async function register(email: string, password: string, language: string, consent: boolean) {
  await api.post('/auth/register', { email, password, language, consent });
}

export async function login(email: string, password: string) {
  const form = new URLSearchParams({ username: email, password });
  const { data } = await api.post<TokenPair>('/auth/login', form.toString(), {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  });
  await saveTokens(data.access_token, data.refresh_token);
}

export async function logout() {
  const refreshToken = await getRefreshToken();
  if (refreshToken) await api.post('/auth/logout', { refresh_token: refreshToken }).catch(() => {});
  await clearTokens();
}

export async function fetchMe() {
  const { data } = await api.get<CurrentUser>('/users/me');
  return data;
}

// Transforme une erreur de l'API en message lisible pour l'utilisateur.
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    if (!error.response) return `Impossible de joindre le serveur (${API_URL}).`;
    const detail = (error.response.data as { detail?: unknown })?.detail;
    if (typeof detail === 'string') return detail;
    if (Array.isArray(detail)) return 'Vérifiez les champs : e-mail valide et mot de passe de 8 caractères minimum.';
  }
  return 'Une erreur inattendue est survenue.';
}
