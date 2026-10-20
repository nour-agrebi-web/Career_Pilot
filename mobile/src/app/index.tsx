import { ScrollView, Text } from 'react-native';

import { useAuth } from '@/lib/auth-context';
import { Button, styles } from '@/lib/ui';

// Écran d'accueil provisoire : il sera remplacé par l'import du CV (Epic 2) puis le fil d'offres.
export default function HomeScreen() {
  const { user, signOut } = useAuth();

  return (
    <ScrollView contentContainerStyle={styles.screen}>
      <Text style={styles.title}>Bienvenue</Text>
      <Text style={styles.subtitle}>Connecté en tant que {user?.email}</Text>
      <Text style={styles.subtitle}>Prochaine étape : importer votre CV.</Text>
      <Button title="Se déconnecter" onPress={signOut} />
    </ScrollView>
  );
}
