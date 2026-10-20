import { Link } from 'expo-router';
import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, Text } from 'react-native';

import { errorMessage } from '@/lib/api';
import { useAuth } from '@/lib/auth-context';
import { Button, Field, styles } from '@/lib/ui';

export default function LoginScreen() {
  const { signIn } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function onSubmit() {
    setError(null);
    setLoading(true);
    try {
      await signIn(email.trim(), password);
    } catch (e) {
      setError(errorMessage(e)); // US-1.2.1 : message unique, sans dire quel champ est faux
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView style={{ flex: 1 }} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.screen} keyboardShouldPersistTaps="handled">
        <Text style={styles.title}>CareerPilot</Text>
        <Text style={styles.subtitle}>Connectez-vous pour retrouver vos offres.</Text>
        <Field placeholder="E-mail" keyboardType="email-address" value={email} onChangeText={setEmail} />
        <Field placeholder="Mot de passe" secureTextEntry value={password} onChangeText={setPassword} />
        {error && <Text style={styles.error}>{error}</Text>}
        <Button title="Se connecter" onPress={onSubmit} loading={loading} disabled={!email || !password} />
        <Link href="/register" style={styles.link}>
          Pas encore de compte ? Créer un compte
        </Link>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
