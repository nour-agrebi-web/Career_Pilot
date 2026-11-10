package tn.careerpilot.domain

/** Statuts d'un CV (CVStatus du diagramme de classes) et textes affichés. */
object CvStatus {
    const val UPLOADED = "UPLOADED"
    const val PROCESSING = "PROCESSING"
    const val PROCESSED = "PROCESSED"
    const val ERROR = "ERROR"

    /** Codes d'erreur renvoyés par l'API. */
    const val UNREADABLE = "UNREADABLE"
    const val NO_TEXT = "NO_TEXT"

    fun isRunning(status: String) = status == UPLOADED || status == PROCESSING

    fun label(status: String) = when (status) {
        UPLOADED -> "importé"
        PROCESSING -> "analyse en cours"
        PROCESSED -> "analysé"
        ERROR -> "erreur d'analyse"
        else -> status.lowercase()
    }

    /** Message d'erreur affiché avec les deux choix : réimporter ou saisir le profil (US-2.2.2, US-2.2.3). */
    fun errorMessage(errorCode: String?, serverMessage: String?) = when (errorCode) {
        UNREADABLE -> "Ce fichier est endommagé ou illisible."
        NO_TEXT -> "Ce CV semble scanné et son texte n'a pas pu être lu."
        else -> serverMessage ?: "L'analyse de ce CV a échoué."
    }

    private val SECTION_LABELS = mapOf(
        "skills" to "compétences", "experiences" to "expériences", "educations" to "formations", "languages" to "langues",
    )

    /** « formations et expériences » : sections vides à compléter (US-2.2.4). */
    fun missingLabel(sections: List<String>): String? {
        val labels = sections.mapNotNull { SECTION_LABELS[it] }
        return when (labels.size) {
            0 -> null
            1 -> labels[0]
            else -> labels.dropLast(1).joinToString(", ") + " et " + labels.last()
        }
    }
}
