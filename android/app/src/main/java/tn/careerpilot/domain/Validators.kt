package tn.careerpilot.domain

/** Règles métier vérifiées dans l'application avant tout appel réseau (le serveur les revérifie). */
object Validators {
    const val MIN_THRESHOLD = 50
    const val MAX_THRESHOLD = 90
    const val MAX_CV_SIZE = 5L * 1024 * 1024
    private val CV_EXTENSIONS = setOf("pdf", "docx")
    private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    fun preferences(prefs: Preferences): List<String> = buildList {
        if (prefs.jobTypes.isEmpty()) add("Choisissez au moins un type d'offre.")
        if (prefs.threshold !in MIN_THRESHOLD..MAX_THRESHOLD) add("Le seuil doit être compris entre 50 et 90.")
        if (prefs.locations.isEmpty() && !prefs.remoteWork) {
            add("Ajoutez une localisation ou acceptez le télétravail.")
        }
    }

    fun location(city: String, existing: List<Location>): String? = when {
        city.isBlank() -> "Indiquez une ville."
        existing.any { it.city.equals(city.trim(), ignoreCase = true) } -> "Cette ville est déjà dans la liste."
        else -> null
    }

    fun cvFile(name: String, size: Long): String? {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            ext !in CV_EXTENSIONS -> "Format non supporté : importez un fichier PDF ou DOCX."
            size > MAX_CV_SIZE -> "Fichier trop volumineux : 5 Mo maximum."
            size == 0L -> "Le fichier est vide."
            else -> null
        }
    }

    fun skill(name: String, existing: List<Skill>, ignoreId: String? = null): String? = when {
        name.isBlank() -> "Indiquez une compétence."
        existing.any { it.id != ignoreId && it.name.trim().equals(name.trim(), ignoreCase = true) } ->
            "« ${name.trim()} » figure déjà dans votre profil."
        else -> null
    }

    /** Dates au format AAAA-MM-JJ ; la fin (facultative) doit suivre le début. */
    fun period(start: String, end: String?): String? = when {
        !DATE.matches(start) -> "Date de début au format AAAA-MM-JJ."
        !end.isNullOrBlank() && !DATE.matches(end) -> "Date de fin au format AAAA-MM-JJ."
        !end.isNullOrBlank() && end < start -> "La date de fin doit suivre la date de début."
        else -> null
    }
}
