import * as SecureStore from 'expo-secure-store';

// Les jetons sont stockés chiffrés (Keystore Android / Keychain iOS), jamais dans AsyncStorage.
const ACCESS_KEY = 'careerpilot.access_token';
const REFRESH_KEY = 'careerpilot.refresh_token';

export function getAccessToken() {
  return SecureStore.getItemAsync(ACCESS_KEY);
}

export function getRefreshToken() {
  return SecureStore.getItemAsync(REFRESH_KEY);
}

export async function saveTokens(accessToken: string, refreshToken: string) {
  await SecureStore.setItemAsync(ACCESS_KEY, accessToken);
  await SecureStore.setItemAsync(REFRESH_KEY, refreshToken);
}

export async function clearTokens() {
  await SecureStore.deleteItemAsync(ACCESS_KEY);
  await SecureStore.deleteItemAsync(REFRESH_KEY);
}
