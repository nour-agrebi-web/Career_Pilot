import { ActivityIndicator, Pressable, StyleSheet, Text, TextInput, TextInputProps } from 'react-native';

export const colors = { primary: '#208AEF', text: '#11181C', muted: '#687076', border: '#D0D7DE', error: '#D92D20' };

export function Field(props: TextInputProps) {
  return <TextInput placeholderTextColor={colors.muted} autoCapitalize="none" {...props} style={styles.input} />;
}

export function Button({ title, onPress, disabled, loading }: { title: string; onPress: () => void; disabled?: boolean; loading?: boolean }) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      style={[styles.button, (disabled || loading) && styles.buttonDisabled]}>
      {loading ? <ActivityIndicator color="#fff" /> : <Text style={styles.buttonText}>{title}</Text>}
    </Pressable>
  );
}

export const styles = StyleSheet.create({
  screen: { flexGrow: 1, justifyContent: 'center', padding: 24, gap: 12, backgroundColor: '#fff' },
  title: { fontSize: 28, fontWeight: '700', color: colors.text },
  subtitle: { fontSize: 15, color: colors.muted, marginBottom: 12 },
  input: { borderWidth: 1, borderColor: colors.border, borderRadius: 10, padding: 14, fontSize: 16, color: colors.text },
  button: { backgroundColor: colors.primary, borderRadius: 10, padding: 15, alignItems: 'center', marginTop: 4 },
  buttonDisabled: { opacity: 0.5 },
  buttonText: { color: '#fff', fontSize: 16, fontWeight: '600' },
  error: { color: colors.error, fontSize: 14 },
  link: { color: colors.primary, textAlign: 'center', marginTop: 8, fontSize: 15 },
});
