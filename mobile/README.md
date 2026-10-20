# CareerPilot mobile

Application React Native (Expo SDK 58, Expo Router, TypeScript).

```text
src/app/          écrans (chaque fichier = une route)
  _layout.tsx     navigation + protection des écrans selon la connexion
  login.tsx       connexion
  register.tsx    inscription avec consentement
  index.tsx       accueil (connecté)
src/lib/
  api.ts          client axios, ajout du JWT, refresh automatique sur 401
  tokens.ts       stockage chiffré des jetons (expo-secure-store)
  auth-context.tsx état de connexion partagé
  ui.tsx          champs, boutons et styles communs
```

Lancer : `npm install`, copier `.env.example` en `.env`, puis `npx expo start`.

Ajouter une dépendance : `npx expo install <paquet>` (choisit la version compatible avec le SDK).
