package tn.careerpilot.domain

import java.text.Normalizer

/**
 * Recherche dans le fil d'offres.
 *
 * - Correspondance par mot entier, sans tenir compte de la casse ni des accents :
 *   « IT » ne trouve plus « littéraire », « developpeur » trouve « Développeur ».
 *   Un mot de 4 lettres ou plus trouve aussi les mots qui commencent par lui (« pyth » → « Python »).
 * - Correspondance par catégorie : « IA », « IT », « informatique », « mécanique », « développement »…
 *   trouvent les offres de cette catégorie. Le scraper enregistre la catégorie de chaque offre
 *   ([Offer.categories], lue sur le site ou déduite) : elle est utilisée telle quelle. Pour une offre
 *   sans catégorie connue (null), elle est déduite d'une liste de mots-clés (repli), à tenir alignée
 *   avec backend/app/modules/offers/search.py et scraper/jobscraper/categories.py.
 * - Les sigles courts (IA, IT, AI…) ne comptent dans un titre ou une description que s'ils y sont
 *   écrits en majuscules, pour ne pas confondre « IT » avec le pronom anglais « it » ni « AI » avec « j'ai ».
 */
object OfferSearch {

    private class Domain(val name: String, val aliases: Set<String>, rawKeywords: List<String>) {
        /** Mots-clés déjà normalisés, calculés une fois et non à chaque frappe. */
        val keywords: List<String> by lazy { rawKeywords.map { tokens(it).joinToString(" ") } }
    }

    private val AI_KEYWORDS = listOf(
        "ia", "ai", "intelligence artificielle", "artificial intelligence", "machine learning", "deep learning",
        "apprentissage automatique", "apprentissage profond", "data scientist", "data science", "nlp", "llm",
        "vision par ordinateur", "computer vision", "reseaux de neurones", "neural network", "tensorflow",
        "pytorch", "scikit learn", "chatbot", "ia generative", "genai",
    )

    private val DEV_KEYWORDS = listOf(
        "developpement", "developpeur", "developpeuse", "developer", "development", "dev", "programmeur",
        "programmation", "programming", "logiciel", "software", "fullstack", "full stack", "frontend",
        "front end", "backend", "back end", "mobile", "android", "ios", "web", "java", "python", "javascript",
        "typescript", "kotlin", "php", "react", "angular", "flutter", "django", "spring", "laravel", "node",
        "nodejs", "dotnet", "c#", "symfony",
    )

    private val IT_KEYWORDS = listOf(
        "it", "informatique", "informaticien", "informaticienne", "devops", "cloud", "cybersecurite",
        "securite informatique", "cybersecurity", "systeme d information", "systemes d information", "si",
        "sysadmin", "administrateur systeme", "administrateur reseau", "reseau informatique", "base de donnees",
        "sql", "data", "helpdesk", "support informatique", "erp", "odoo", "sap", "linux", "docker", "kubernetes",
    ) + DEV_KEYWORDS + AI_KEYWORDS

    private val DOMAINS = listOf(
        Domain("IA", setOf("ia", "ai", "intelligence artificielle", "artificial intelligence", "machine learning"), AI_KEYWORDS),
        Domain("IT", setOf("it", "informatique", "info", "tic", "technologies de l information"), IT_KEYWORDS),
        Domain(
            "Développement",
            setOf("developpement", "developpeur", "developpeuse", "developper", "dev", "programmation", "developer", "development"),
            DEV_KEYWORDS,
        ),
        Domain(
            "Mécanique",
            setOf("mecanique", "meca", "mecanicien", "mecanicienne", "genie mecanique"),
            listOf(
                "mecanique", "mecanicien", "mecanicienne", "genie mecanique", "electromecanique", "electromecanicien",
                "maintenance industrielle", "usinage", "soudure", "soudeur", "chaudronnerie", "chaudronnier",
                "tourneur", "fraiseur", "cao", "solidworks", "catia", "autocad", "productique", "automobile",
                "hydraulique", "pneumatique",
            ),
        ),
        Domain(
            "Électrique",
            setOf("electrique", "electricite", "electronique", "electricien", "genie electrique"),
            listOf(
                "electrique", "electricite", "electricien", "electronique", "electronicien", "genie electrique",
                "electrotechnique", "automatisme", "automaticien", "plc", "cablage", "electromecanique",
            ),
        ),
        Domain(
            "Finance",
            setOf("finance", "comptabilite", "comptable", "audit", "banque"),
            listOf(
                "finance", "financier", "financiere", "comptabilite", "comptable", "audit", "auditeur", "fiscalite",
                "controle de gestion", "controleur de gestion", "tresorerie", "banque", "bancaire", "assurance",
            ),
        ),
        Domain(
            "Marketing",
            setOf("marketing", "communication", "digital marketing", "marketing digital"),
            listOf(
                "marketing", "communication", "community manager", "seo", "sea", "content", "contenu",
                "reseaux sociaux", "social media", "brand", "marque", "publicite",
            ),
        ),
        Domain(
            "Commercial",
            setOf("commercial", "vente", "ventes", "commerciale"),
            listOf(
                "commercial", "commerciale", "vente", "ventes", "vendeur", "vendeuse", "business developer",
                "account manager", "chef de rayon", "televente", "teleconseiller", "prospection",
            ),
        ),
        Domain(
            "RH",
            setOf("rh", "ressources humaines", "recrutement"),
            listOf("rh", "ressources humaines", "recrutement", "recruteur", "talent acquisition", "paie", "gestion du personnel"),
        ),
    )

    /** Sigles qui ne comptent que s'ils sont écrits en majuscules dans un titre ou une description. */
    private val ACRONYMS = setOf("ia", "ai", "it", "si", "sea", "tic")

    /** Vrai si l'offre correspond à la recherche [query] (vide = toutes les offres). */
    fun matches(offer: Offer, query: String): Boolean {
        val normalizedQuery = tokens(query).joinToString(" ")
        if (normalizedQuery.isEmpty()) return true
        val text = OfferText(offer)
        // La recherche entière peut désigner une catégorie (« intelligence artificielle »).
        domainOf(normalizedQuery)?.let { if (inCategory(offer, it, text)) return true }
        // Sinon chaque mot doit être trouvé, comme mot entier ou comme catégorie.
        return normalizedQuery.split(' ').all { word ->
            text.hasWord(word) || domainOf(word)?.let { inCategory(offer, it, text) } == true
        }
    }

    /** Catégorie enregistrée par le scraper si l'offre en a une ; sinon déduite des mots-clés (repli). */
    private fun inCategory(offer: Offer, domain: Domain, text: OfferText): Boolean =
        offer.categories?.contains(domain.name) ?: text.hasAny(domain.keywords)

    private fun domainOf(normalized: String): Domain? = DOMAINS.firstOrNull { normalized in it.aliases }

    /** Texte de l'offre découpé en mots normalisés. */
    private class OfferText(offer: Offer) {
        private val prose = listOf(offer.title, offer.description, offer.company, offer.city.orEmpty())
        private val proseWords = prose.flatMap { tokens(it) }
        // Mêmes mots, mais en ne gardant les sigles que s'ils étaient en majuscules.
        private val proseStrict = " " + prose.joinToString(" | ") { strictTokens(it).joinToString(" ") } + " "
        private val skillWords = offer.requiredSkills.flatMap { tokens(it) }
        private val skills = " " + offer.requiredSkills.joinToString(" | ") { tokens(it).joinToString(" ") } + " "
        private val all = proseWords + skillWords

        fun hasWord(word: String): Boolean {
            if (word in ACRONYMS) return containsPhrase(word)
            return all.any { it == word || (word.length >= 4 && it.startsWith(word)) }
        }

        fun hasAny(keywords: List<String>): Boolean = keywords.any { containsPhrase(it) }

        private fun containsPhrase(phrase: String): Boolean =
            phrase.isNotEmpty() && (" $phrase " in proseStrict || " $phrase " in skills)
    }

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val SEPARATORS = Regex("[^\\p{L}\\p{N}#+]+")

    private fun stripAccents(text: String): String =
        COMBINING_MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "")

    /** Mots en minuscules, sans accents ; la ponctuation sépare les mots (« C# » et « C++ » sont gardés). */
    internal fun tokens(text: String): List<String> =
        SEPARATORS.split(stripAccents(text).lowercase()).filter { it.isNotEmpty() }

    /** Comme [tokens], mais un sigle écrit en minuscules (« it », « j'ai ») est remplacé par un blanc. */
    private fun strictTokens(text: String): List<String> =
        SEPARATORS.split(stripAccents(text)).filter { it.isNotEmpty() }.map { raw ->
            val lower = raw.lowercase()
            if (lower in ACRONYMS && raw != raw.uppercase()) "_" else lower
        }
}
