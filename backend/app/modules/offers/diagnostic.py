"""Pourquoi une offre en base n'apparaît-elle pas dans « Mes offres » ? Diagnostic en lecture seule.

    python -m app.modules.offers.diagnostic nour.agrebi@etudiant.tn

Suit le même chemin que l'app (POST /matches/refresh puis GET /matches) et compte, étape par étape, combien
d'offres passent : profil validé, statut, type, ville, langue, score au-dessus du seuil, match enregistré.
Ne modifie rien. Le score est calculé ici par mots-clés (sans Ollama), donc à quelques points près.
"""
import sys
from collections import Counter
from pymongo.database import Database
from app.modules.offers.matching import eliminated, score
from app.modules.preferences.router import get_preferences


def diagnose(db: Database, email: str) -> list[str]:
    out: list[str] = []
    user = db.users.find_one({"email": email.strip().lower()})
    if not user:
        return [f"Aucun compte avec l'e-mail {email}."]
    profile = db.profiles.find_one({"user_id": user["_id"]}) or {}
    prefs = get_preferences(db, user["_id"])
    total = db.offers.count_documents({})
    active = db.offers.count_documents({"status": "ACTIVE"})
    scraped = db.offers.count_documents({"content_hash": {"$exists": True}})
    vectorized = db.offers.count_documents({"status": "ACTIVE", "vector_index": {"$exists": True}})
    out += [f"Offres en base : {total} (dont {scraped} du scraper), actives : {active}, "
            f"vectorisées : {vectorized}/{active}",
            f"Préférences : types {prefs['job_types']}, villes {[l['city'] for l in prefs.get('locations', [])] or 'toutes'}, "
            f"seuil {prefs['threshold']}"]

    if not profile.get("validated"):
        out.append("BLOQUÉ : le profil n'est pas validé. L'actualisation est refusée (409) et le fil reste vide. "
                   "Validez le profil dans l'app (onglet Profil), puis touchez « Actualiser ».")
        return out

    reasons: Counter = Counter()
    rejected_cities: Counter = Counter()
    scores: list[int] = []
    for offer in db.offers.find({"status": "ACTIVE"}):
        why = eliminated(profile, prefs, offer)
        if why:
            reasons[why] += 1
            if why == "zone hors périmètre":
                rejected_cities[(offer.get("location") or {}).get("city") or "(vide)"] += 1
            continue
        result = score(profile, prefs, offer)
        scores.append(result.score if result else 0)
    above = sum(s >= prefs["threshold"] for s in scores)

    out.append(f"Éliminées par les filtres : {sum(reasons.values())}/{active}")
    for why, n in reasons.most_common():
        out.append(f"  - {why} : {n}")
    if rejected_cities:
        out.append("  villes des offres écartées (les plus fréquentes) : "
                   + ", ".join(f"{c} ({n})" for c, n in rejected_cities.most_common(8)))
    if scores:
        buckets = Counter(min(s // 10 * 10, 90) for s in scores)
        out.append("Scores des offres restantes : "
                   + ", ".join(f"{b}-{b + 9}: {buckets[b]}" for b in sorted(buckets, reverse=True)))
    out.append(f"Au-dessus du seuil {prefs['threshold']} : {above}")

    matches = db.matches.count_documents({"user_id": user["_id"]})
    visible = db.matches.count_documents({"user_id": user["_id"], "score": {"$gte": prefs["threshold"]},
                                          "status": {"$ne": "REJECTED"}})
    out.append(f"Correspondances enregistrées : {matches}, visibles dans le fil : {visible}")

    if not scores:
        out.append("BLOQUÉ : toutes les offres sont écartées par les filtres ci-dessus. Élargissez les types "
                   "ou les villes dans Préférences.")
    elif above == 0:
        out.append(f"BLOQUÉ : aucune offre n'atteint le seuil {prefs['threshold']}. Baissez le seuil dans "
                   "Préférences, puis « Actualiser ».")
    elif visible == 0:
        out.append(f"BLOQUÉ : {above} offre(s) passent, mais aucune correspondance n'est enregistrée : "
                   "l'actualisation n'a pas abouti. Touchez « Actualiser » dans Mes offres et regardez les "
                   "erreurs dans la console de l'API.")
    else:
        out.append(f"OK : {visible} offre(s) devraient s'afficher. Si le fil est vide, touchez « Actualiser » "
                   "(l'app garde un cache local).")
    return out


def main(argv: list[str] | None = None) -> int:
    argv = sys.argv[1:] if argv is None else argv
    if len(argv) != 1:
        print(__doc__)
        return 2
    from app.db.mongo import get_db
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print("\n".join(diagnose(get_db(), argv[0])))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
