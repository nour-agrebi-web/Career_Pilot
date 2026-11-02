package tn.careerpilot.domain

fun offer(type: String = "stage", title: String = "Développeur Kotlin", company: String = "Nexa", city: String = "Tunis",
          date: String = "2026-10-01", skills: List<String> = listOf("Kotlin")) =
    Offer("o-$title", title, company, city, "Tunisie", type, "hybride", "stage", "", skills, "TanitJobs", null, date)

fun match(id: String, score: Int, type: String = "stage", status: MatchStatus = MatchStatus.PENDING,
          missing: List<String> = emptyList(), title: String = "Offre $id", date: String = "2026-10-01") =
    Match(id, score, ScoreBreakdown(0, 0, 0, 0), emptyList(), missing, status, "2026-10-01T00:00:00Z",
        offer(type = type, title = title, date = date))
