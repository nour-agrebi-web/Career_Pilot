import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { Button, colors, Field, styles } from '@/lib/ui';

const LANGUAGES = [
  { code: 'fr', label: 'Français' },
  { code: 'en', label: 'English' },
  { code: 'ar', label: 'العربية' },
];

export default function RegisterScreen() {
  const { signUp } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [language, setLanguage] = useState('fr');
  const [consent, setConsent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit() {
    setError(null);
    setLoading(true);
    try {
      await signUp(email.trim(), password, language, consent);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.screen} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>Créer un compte</Text>
        <Text style={styles.subtitle}>Déposez ensuite votre CV, l'agent s'occupe du reste.</Text>
        <Field placeholder="E-mail" keyboardType="email-address" value={email} onChangeText={setEmail} />
        <Field placeholder="Mot de passe (8 caractères minimum)" secureTextEntry value={password} onChangeText={setPassword} />

        <View style={local.languages}>
          {LANGUAGES.map((l) => (
            <Pressable key={l.code} onPress={() => setLanguage(l.code)} style={[local.chip, language === l.code && local.chipActive]}>
              <Text style={[local.chipText, language === l.code && local.chipTextActive]}>{l.label}</Text>
            </Pressable>
          ))}
        </View>

        {/* US-1.1.2 : consentement explicite, l'inscription est bloquée tant qu'il n'est pas coché */}
        <Pressable style={local.consent} onPress={() => setConsent(!consent)}>
          <View style={[local.box, consent && local.boxChecked]}>{consent && <Text style={local.check}>✓</Text>}</View>
          <Text style={local.consentText}>
            J'accepte que CareerPilot traite mon CV et mes données pour me proposer des offres, conformément à la loi
            organique tunisienne n° 2004-63 et, pour les utilisateurs de l'Union européenne, au RGPD. Mes coordonnées ne
            sont jamais envoyées aux services d'IA et je peux supprimer mes données à tout moment.
          </Text>
        </Pressable>

        {error && <Text style={styles.error}>{error}</Text>}
        <Button title="Créer mon compte" onPress={onSubmit} loading={loading} disabled={!consent || !email || password.length < 8} />
        <Link href="/login" style={styles.link}>
          Déjà un compte ? Se connecter
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const local = StyleSheet.create({
  languages: { flexDirection: 'row', gap: 8 },
  chip: { borderWidth: 1, borderColor: colors.border, borderRadius: 20, paddingVertical: 8, paddingHorizontal: 14 },
  chipActive: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipText: { color: colors.text },
  chipTextActive: { color: '#fff', fontWeight: '600' },
  consent: { flexDirection: 'row', gap: 10, alignItems: 'flex-start' },
  box: { width: 22, height: 22, borderWidth: 2, borderColor: colors.primary, borderRadius: 5, marginTop: 2, alignItems: 'center', justifyContent: 'center' },
  boxChecked: { backgroundColor: colors.primary },
  check: { color: '#fff', fontWeight: '700' },
  consentText: { flex: 1, fontSize: 13, color: colors.muted, lineHeight: 18 },
});
