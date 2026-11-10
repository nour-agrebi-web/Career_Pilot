package tn.careerpilot.data

/** Stockage des jetons JWT. L'implémentation Android les chiffre avec l'Android Keystore. */
interface TokenStore {
    val accessToken: String?
    val refreshToken: String?
    fun save(accessToken: String, refreshToken: String)
    fun clear()
}
