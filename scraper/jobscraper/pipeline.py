"""Orchestration d'une exécution : lister -> dédupliquer/enregistrer -> lire les détails utiles -> scorer."""
from __future__ import annotations

import logging
import time

from .http import RobotsDisallowed
from .normalize import finalize
from .scoring import profile_hash, score_offer
from .sources import SOURCES
from .storage import Storage

log = logging.getLogger(__name__)


def collect(storage: Storage, client, source_names: list[str], max_details: int = 40) -> dict:
    """Collecte les offres. `max_details` limite les pages détail lues par source et par exécution :
    le reste est repris aux exécutions suivantes (montée en charge douce lors de la 1re exécution)."""
    stats: dict = {}
    for name in source_names:
        src = SOURCES[name]()
        st = {"listees": 0, "nouvelles": 0, "modifiees": 0, "inchangees": 0, "details_lus": 0,
              "details_en_attente": 0, "inactives": 0, "erreurs": 0, "liste_304": False}
        stats[name] = st
        before = client.requests_sent
        try:
            listing = src.list_offers(client)
        except Exception as e:  # noqa: BLE001
            log.error("%s : liste impossible à lire (%s)", name, e)
            st["erreurs"] += 1
            continue
        st["listees"] = len(listing.items)
        st["liste_304"] = listing.not_modified

        candidates = []
        for item in listing.items:
            existing = storage.get_offer(item["source"], item["source_id"])
            if item.get("insertable"):
                _, state = storage.upsert_offer(finalize(dict(item)))
                st[{"new": "nouvelles", "updated": "modifiees", "unchanged": "inchangees"}[state]] += 1
                existing = storage.get_offer(item["source"], item["source_id"])
            needs = (existing is None or not existing.get("detail_fetched")
                     or (item.get("source_lastmod") and item["source_lastmod"] != existing.get("source_lastmod")))
            if needs and not item.get("detail_fetched"):
                candidates.append(item)
            elif not item.get("insertable"):
                st["inchangees"] += 1
        if listing.complete:
            st["inactives"] = storage.mark_inactive(src.label, (i["source_id"] for i in listing.items))

        # Les plus récentes d'abord ; le reste attend la prochaine exécution.
        candidates.sort(key=lambda i: (i.get("published_at") or i.get("source_lastmod") or "", i["source_id"]),
                        reverse=True)
        st["details_en_attente"] = max(0, len(candidates) - max_details)
        for item in candidates[:max_details]:
            try:
                resp = client.get(src.detail_url(item))
                offer = src.parse_detail(item, resp.body)
            except RobotsDisallowed:
                log.info("%s : %s interdit par robots.txt, ignoré", name, item["url"])
                continue
            except Exception as e:  # noqa: BLE001 - une page en erreur ne bloque pas les autres
                log.warning("%s : détail %s illisible (%s)", name, item["url"], e)
                st["erreurs"] += 1
                continue
            if not offer or not offer.get("title"):
                st["erreurs"] += 1
                continue
            existed = storage.get_offer(item["source"], item["source_id"]) is not None
            storage.upsert_offer(finalize(offer))
            st["details_lus"] += 1
            if not item.get("insertable"):     # Tanitjobs : l'offre n'existe en base qu'après lecture du détail
                st["nouvelles" if not existed else "modifiees"] += 1
        st["requetes_http"] = client.requests_sent - before
    return stats


def score_all(storage: Storage, profile: dict) -> dict:
    """Score chaque offre nouvelle ou modifiée (ou toutes si le profil a changé). Tous les scores sont stockés,
    y compris sous le seuil (collection offer_scores) : le seuil n'est appliqué qu'à la lecture (`Storage.matches`)."""
    ph = profile_hash(profile)
    rows = storage.offers_to_score(ph)
    for row in rows:
        score, details = score_offer(row, profile)
        storage.save_score(row["_id"], ph, row["content_hash"], score, details)
    storage.commit()
    return {"profil": ph, "offres_scorees": len(rows)}


def run(storage: Storage, client, profile: dict, source_names: list[str], max_details: int = 40) -> dict:
    run_id = storage.start_run()
    t0 = time.time()
    stats = {"sources": collect(storage, client, source_names, max_details)}
    stats["score"] = score_all(storage, profile)
    stats["duree_s"] = round(time.time() - t0, 1)
    stats["requetes_http_total"] = client.requests_sent
    storage.finish_run(run_id, stats)
    return stats
