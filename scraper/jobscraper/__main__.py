"""Ligne de commande.

  python -m jobscraper run   [--sources keejob,tanitjobs] [--max-details 40] [--profile profile.json]
  python -m jobscraper watch [--pause 30] [--rattrapage 1] [--max-details 40]   # en continu, Ctrl+C pour arrêter
  python -m jobscraper list  [--seuil 70] [--limit 20]      # lecture seule : aucune requête réseau
  python -m jobscraper rescore                              # recalcule après modification du profil, hors ligne
  python -m jobscraper stats
  python -m jobscraper categories [--toutes]                # rattrape la catégorie des offres en base, hors ligne
"""
from __future__ import annotations

import argparse
import json
import logging
import os
import sys

from pymongo import MongoClient

from .http import PoliteClient
from .pipeline import run, score_all, watch
from .scoring import profile_hash
from .sources import SOURCES
from .storage import Storage


def main(argv: list[str] | None = None, mongo_client=None) -> int:
    ap = argparse.ArgumentParser(prog="jobscraper", description="Collecte et score d'offres d'emploi")
    ap.add_argument("--mongo-url", default=os.getenv("MONGODB_URL", "mongodb://localhost:27017"),
                    help="même variable que le backend (MONGODB_URL)")
    ap.add_argument("--db", default=os.getenv("MONGODB_DB", "careerpilot"),
                    help="même base que le backend (MONGODB_DB, « careerpilot » par défaut)")
    ap.add_argument("--profile", default="profile.example.json")
    ap.add_argument("-v", "--verbose", action="store_true")
    sub = ap.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run", help="collecter puis scorer")
    r.add_argument("--sources", default=",".join(SOURCES))
    r.add_argument("--max-details", type=int, default=40, help="pages détail par site et par exécution (0 = sans limite)")
    r.add_argument("--delay", type=float, default=2.0, help="pause en secondes entre 2 requêtes au même site")
    w = sub.add_parser("watch", help="tourner en continu (Ctrl+C pour arrêter)")
    w.add_argument("--sources", default=",".join(SOURCES))
    w.add_argument("--max-details", type=int, default=40, help="pages détail par site et par cycle (0 = sans limite)")
    w.add_argument("--delay", type=float, default=2.0, help="pause en secondes entre 2 requêtes au même site")
    w.add_argument("--pause", type=float, default=30.0, help="minutes entre deux cycles quand tout est à jour")
    w.add_argument("--rattrapage", type=float, default=1.0,
                   help="minutes entre deux cycles tant qu'il reste des détails en attente")
    ls = sub.add_parser("list", help="offres au-dessus du seuil (lecture seule)")
    ls.add_argument("--seuil", type=int, default=None)
    ls.add_argument("--limit", type=int, default=20)
    sub.add_parser("rescore", help="scorer sans réseau (profil modifié)")
    sub.add_parser("stats", help="répartition des scores et dernière exécution")
    c = sub.add_parser("categories", help="rattraper la catégorie des offres déjà en base (sans réseau)")
    c.add_argument("--toutes", action="store_true", help="recalculer aussi les offres qui ont déjà une catégorie")
    a = ap.parse_args(argv)

    logging.basicConfig(level=logging.INFO if a.verbose else logging.WARNING,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")   # accents corrects dans la console Windows
    with open(a.profile, encoding="utf-8") as f:
        profile = json.load(f)
    client_db = mongo_client or MongoClient(a.mongo_url, tz_aware=True, serverSelectionTimeoutMS=5000)
    storage = Storage(client_db[a.db])
    ph = profile_hash(profile)

    names = [s.strip() for s in getattr(a, "sources", "").split(",") if s.strip()]
    unknown = set(names) - set(SOURCES)
    if unknown:
        ap.error(f"source(s) inconnue(s) : {', '.join(sorted(unknown))}")

    if a.cmd == "watch":
        if a.pause <= 0 or a.rattrapage <= 0 or a.delay < 1:
            ap.error("--pause et --rattrapage doivent être > 0, et --delay au moins 1 seconde (politesse)")

        def load_profile():
            with open(a.profile, encoding="utf-8") as f:
                return json.load(f)

        print(f"Mode continu : {', '.join(names)} | pause {a.pause:g} min (rattrapage {a.rattrapage:g} min) "
              f"| {a.delay:g} s entre requêtes. Ctrl+C pour arrêter.", flush=True)
        watch(storage, lambda: PoliteClient(storage, delay=a.delay), load_profile, names, a.max_details,
              pause_min=a.pause, catchup_min=a.rattrapage, report=lambda m: print(m, flush=True))
    elif a.cmd == "run":
        client = PoliteClient(storage, delay=a.delay)
        print(json.dumps(run(storage, client, profile, names, a.max_details), indent=2, ensure_ascii=False,
                         default=str))
    elif a.cmd == "rescore":
        print(json.dumps(score_all(storage, profile), ensure_ascii=False))
    elif a.cmd == "list":
        seuil = a.seuil if a.seuil is not None else profile.get("threshold", 70)
        rows = storage.matches(ph, seuil, limit=a.limit)
        print(f"{len(rows)} offre(s) avec un score >= {seuil} (profil {ph})")
        for row in rows:
            d = row["details"]
            loc = row.get("location") or {}
            flag = " (score partiel)" if d.get("partiel") else ""
            print(f"{row['score']:>3}  {row['title']} | {row.get('company') or '?'} | {loc.get('city') or '?'} "
                  f"| {row['source']}{flag}\n     {row['url']}\n     trouvées : {', '.join(d['trouvees']) or '-'}")
    elif a.cmd == "categories":
        st = storage.backfill_categories(all_offers=a.toutes)
        print(f"{sum(st.values())} offre(s) traitée(s) : {st['site']} avec la rubrique du site, "
              f"{st['deduite']} déduite(s) du texte, {st['sans_categorie']} sans catégorie.")
        for row in storage.category_counts():
            print(f"  {row['_id'] or '(aucune)'} : {row['n']}")
    elif a.cmd == "stats":
        for st in storage.source_stats():
            print(f"{st['_id']}: {st['n']} offres ({st['detail']} avec détail, {st['actives']} actives)")
        print("Répartition des scores :")
        for b in storage.score_histogram(ph):
            lo = int(b["_id"])
            print(f"  {lo:>3}-{lo + 9:<3} {'#' * min(60, b['n'])} {b['n']}")
        last = storage.last_run()
        if last:
            print("Dernière exécution :", json.dumps(last["stats"], ensure_ascii=False, default=str))
    if mongo_client is None:
        storage.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
