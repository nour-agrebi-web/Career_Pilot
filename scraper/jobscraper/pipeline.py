"""Orchestration d'une exécution : lister -> dédupliquer/enregistrer -> lire les détails utiles -> scorer."""
from __future__ import annotations

import logging
import time
import urllib.error

from .http import RobotsDisallowed
from .normalize import finalize
from .scoring import profile_hash, score_offer
from .sources import SOURCES
from .storage import Storage

log = logging.getLogger(__name__)


def collect(storage: Storage, client, source_names: list[str], max_details: int = 40) -> dict:
    """Collecte les offres. `max_details` limite les pages détail lues par source et par exécution
    (0 = pas de limite) : le reste est repris aux exécutions suivantes, les plus récentes d'abord."""
    stats: dict = {}
    for name in source_names:
        src = SOURCES[name]()
        st = {"listees": 0, "nouvelles": 0, "modifiees": 0, "inchangees": 0, "details_lus": 0,
              "details_en_attente": 0, "inactives": 0, "retirees": 0, "abandonnees": 0, "erreurs": 0,
              "liste_304": False, "detail_erreurs": []}
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
                if storage.given_up(src.detail_url(item)):
                    st["abandonnees"] += 1          # déjà 3 échecs : on ne réessaie plus cette page
                else:
                    candidates.append(item)
            elif not item.get("insertable"):
                st["inchangees"] += 1
        if listing.complete:
            st["inactives"] = storage.mark_inactive(src.label, (i["source_id"] for i in listing.items))

        # Les plus récentes d'abord ; le reste attend la prochaine exécution.
        candidates.sort(key=lambda i: (i.get("published_at") or i.get("source_lastmod") or "", i["source_id"]),
                        reverse=True)
        limit = max_details if max_details > 0 else len(candidates)
        st["details_en_attente"] = max(0, len(candidates) - limit)

        def fail(url: str, reason: str) -> None:
            st["erreurs"] += 1
            n = storage.record_failure(url, reason)
            log.warning("%s : détail %s en échec (%s), essai %d/%d", name, url, reason, n, storage.MAX_FAILURES)
            if len(st["detail_erreurs"]) < 10:
                st["detail_erreurs"].append({"url": url, "raison": reason})

        for item in candidates[:limit]:
            url = src.detail_url(item)
            try:
                resp = client.get(url)
                offer = src.parse_detail(item, resp.body)
            except RobotsDisallowed:
                log.info("%s : %s interdit par robots.txt, ignoré", name, url)
                continue
            except urllib.error.HTTPError as e:
                if e.code in (404, 410):          # l'offre a été retirée entre la liste et le détail
                    storage.expire(item["source"], item["source_id"])
                    storage.clear_failure(url)
                    st["retirees"] += 1
                else:
                    fail(url, f"HTTP {e.code}")
                continue
            except Exception as e:  # noqa: BLE001 - une page en erreur ne bloque pas les autres
                fail(url, f"{type(e).__name__}: {e}")
                continue
            if not offer or not offer.get("title"):
                fail(url, "page lue mais description introuvable")
                continue
            storage.clear_failure(url)
            existed = storage.get_offer(item["source"], item["source_id"]) is not None
            storage.upsert_offer(finalize(offer))
            st["details_lus"] += 1
            if not item.get("insertable"):     # Tanitjobs : l'offre n'existe en base qu'après lecture du détail
                st["nouvelles" if not existed else "modifiees"] += 1
        st["requetes_http"] = client.requests_sent - before
        if not st["detail_erreurs"]:
            del st["detail_erreurs"]
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
    stats = {"offres_completees": storage.backfill_matching_fields(),
             "categories_rattrapees": storage.backfill_categories(),
             "sources": collect(storage, client, source_names, max_details)}
    stats["score"] = score_all(storage, profile)
    stats["duree_s"] = round(time.time() - t0, 1)
    stats["requetes_http_total"] = client.requests_sent
    storage.finish_run(run_id, stats)
    return stats


def pending_details(stats: dict) -> int:
    return sum(s.get("details_en_attente", 0) for s in stats.get("sources", {}).values())


def watch(storage: Storage, make_client, profile_loader, source_names: list[str], max_details: int = 40,
          pause_min: float = 30.0, catchup_min: float = 1.0, max_cycles: int | None = None,
          sleep=time.sleep, report=print) -> int:
    """Mode continu : un cycle complet (liste, détails, scores), puis une pause, et ainsi de suite.

    - Tant qu'il reste des pages détail en attente (rattrapage), la pause est courte (`catchup_min`).
    - Sinon la pause est longue (`pause_min`) : les sites ne changent pas d'une minute à l'autre.
    - Les règles de politesse restent actives à chaque cycle : robots.txt relu, 2 s entre deux requêtes
      au même site, requêtes conditionnelles, abandon d'une page après 3 échecs.
    - Ctrl+C arrête la boucle proprement : le cycle en cours s'interrompt, tout ce qui est déjà
      enregistré reste en base, et la prochaine exécution reprend là où elle s'était arrêtée.
    - Le profil est relu à chaque cycle : une modification est prise en compte sans relancer.
    Renvoie le nombre de cycles terminés."""
    cycles = 0
    try:
        while max_cycles is None or cycles < max_cycles:
            stats = run(storage, make_client(), profile_loader(), source_names, max_details)
            cycles += 1
            waiting = pending_details(stats)
            delay = catchup_min if waiting else pause_min
            parts = [f"{n}: +{s['nouvelles']} nouvelles, {s['details_lus']} détails, {s['erreurs']} erreurs"
                     for n, s in stats["sources"].items()]
            report(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] cycle {cycles} : {' | '.join(parts)} "
                   f"| {stats['score']['offres_scorees']} scorées | {waiting} détails en attente "
                   f"| prochain cycle dans {delay:g} min (Ctrl+C pour arrêter)")
            if max_cycles is not None and cycles >= max_cycles:
                break
            sleep(delay * 60)
    except KeyboardInterrupt:
        report(f"Arrêt demandé (Ctrl+C) après {cycles} cycle(s) complet(s). Les données déjà collectées sont en base.")
    return cycles
