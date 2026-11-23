import json
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

import mongomock  # noqa: E402

from jobscraper.http import PoliteClient, RobotsDisallowed  # noqa: E402
from jobscraper.normalize import normalize_url  # noqa: E402
from jobscraper.pipeline import run, score_all  # noqa: E402
from jobscraper.scoring import profile_hash, score_offer  # noqa: E402
from jobscraper.sources.keejob import Keejob  # noqa: E402
from jobscraper.sources.tanitjobs import Tanitjobs  # noqa: E402
from jobscraper.storage import Storage, to_doc  # noqa: E402
from tests.fakeweb import FIX, FakeWeb  # noqa: E402

PROFILE = json.loads((ROOT / "profile.example.json").read_text(encoding="utf-8"))
SOURCES = ["keejob", "tanitjobs"]


def new_storage():
    """Base MongoDB simulée en mémoire (mongomock), comme les tests du backend."""
    return Storage(mongomock.MongoClient(tz_aware=True)["careerpilot_test"])


class Clock:
    """Horloge simulée : les pauses ne ralentissent pas les tests mais sont mesurées."""
    def __init__(self):
        self.t, self.slept = 0.0, []

    def now(self):
        return self.t

    def sleep(self, s):
        self.slept.append(s)
        self.t += s


def make(web, storage, delay=2.0, clock=None):
    clock = clock or Clock()
    return PoliteClient(storage, delay=delay, opener=web, sleep=clock.sleep, clock=clock.now), clock


class ParsingTests(unittest.TestCase):
    def test_keejob_liste_complete(self):
        items = Keejob().parse_list((FIX / "keejob_llms_full.txt").read_text(encoding="utf-8"))
        self.assertEqual(len(items), 4)
        first = items[0]
        self.assertEqual(first["source_id"], "900001")
        self.assertEqual(first["company"], "Nexa Digital")
        self.assertEqual(first["city"], "Tunis")
        self.assertEqual(first["contract"], "emploi")
        self.assertEqual(first["work_mode"], "hybride")
        self.assertEqual(first["published_at"], "2026-11-23")
        self.assertTrue(first["url"].endswith("/developpeur-python-fastapi-hf/"))
        self.assertEqual(items[2]["work_mode"], None)          # pas de mode indiqué
        self.assertEqual(items[3]["contract"], "stage")

    def test_keejob_detail_markdown(self):
        item = Keejob().parse_list((FIX / "keejob_llms_full.txt").read_text(encoding="utf-8"))[0]
        offer = Keejob().parse_detail(item, (FIX / "keejob_900001.md").read_text(encoding="utf-8"))
        self.assertIn("FastAPI et MongoDB", offer["description"])
        self.assertNotIn("Postulez", offer["description"])      # section « Postuler » exclue
        self.assertEqual(offer["expires_at"], "2026-12-23")
        self.assertIn("Langues : Français, Anglais", offer["description"])
        self.assertTrue(offer["detail_fetched"])

    def test_keejob_rss(self):
        items = Keejob().parse_rss((FIX / "keejob_rss.xml").read_text(encoding="utf-8"))
        self.assertEqual([i["source_id"] for i in items], ["900001", "900005"])
        self.assertEqual(items[0]["company"], "Nexa Digital")
        self.assertEqual(items[0]["published_at"], "2026-11-23")
        self.assertTrue(items[0]["detail_fetched"])

    def test_tanitjobs_sitemap_ignore_pages_non_offres(self):
        items = Tanitjobs().parse_sitemap((FIX / "tanitjobs_sitemap.xml").read_text(encoding="utf-8"))
        self.assertEqual([i["source_id"] for i in items], ["700001", "700002", "700003"])
        self.assertEqual(items[0]["source_lastmod"], "2026-11-20")

    def test_tanitjobs_jsonld_dans_graph(self):
        o = Tanitjobs().parse_detail({"source": "tanitjobs", "source_id": "700001", "url": "u"},
                                     (FIX / "tanitjobs_700001.html").read_text(encoding="utf-8"))
        self.assertEqual(o["title"], "Tech Lead (C# .Net / Angular) (H/F)")
        self.assertEqual(o["company"], "Aero Systems Tunisie")
        self.assertEqual((o["city"], o["country"]), ("Nabeul", "Tunisie"))
        self.assertEqual(o["contract"], "emploi")
        self.assertEqual((o["published_at"], o["expires_at"]), ("2026-11-09", "2026-12-09"))
        self.assertIn("C# .NET et Angular", o["description"])
        self.assertNotIn("<li>", o["description"])

    def test_tanitjobs_secours_sans_jsonld(self):
        o = Tanitjobs().parse_detail({"source": "tanitjobs", "source_id": "700003", "url": "u"},
                                     (FIX / "tanitjobs_700003.html").read_text(encoding="utf-8"))
        self.assertEqual(o["title"], "Comptable confirmé (H/F)")
        self.assertIn("Sage", o["description"])

    def test_normalisation_url(self):
        self.assertEqual(normalize_url("HTTPS://WWW.Keejob.com/offres-emploi/1/x?utm_source=a#top"),
                         "https://www.keejob.com/offres-emploi/1/x/")


class ScoringTests(unittest.TestCase):
    def offer(self, **kw):
        base = dict(title="", description="", company="", city=None, work_mode=None, contract=None,
                    detail_fetched=True, source="T", source_id="1", url="u", content_hash="h", fingerprint="f")
        base.update(kw)
        return to_doc(base)      # même format que les documents de la collection offers

    def test_bornes_csharp_dotnet_et_accents(self):
        p = {"skills": {"c#": 3, ".net": 3, "c": 1}, "saturation": 6}
        s, d = score_offer(self.offer(title="Développeur C# / .NET"), p)
        self.assertEqual(sorted(d["trouvees"]), [".net", "c#"])  # « c » seul n'est pas trouvé dans « c# »
        p2 = {"skills": {"developpeur": 1}, "titles": ["développeur"]}
        _, d2 = score_offer(self.offer(title="DÉVELOPPEUR"), p2)
        self.assertEqual(d2["trouvees"], ["developpeur"])
        self.assertEqual(d2["intitule"], 25)

    def test_exclusion_plafonne(self):
        s, d = score_offer(self.offer(title="Commercial Python SQL Kotlin Android", city="Tunis"), PROFILE)
        self.assertLessEqual(s, 20)
        self.assertEqual(d["exclusions"], ["commercial"])
        _, d2 = score_offer(self.offer(title="Conseillers Clients Germanophones"), PROFILE)
        self.assertEqual(d2["exclusions"], ["conseiller client"])     # pluriel reconnu

    def test_score_borne_0_100(self):
        s, _ = score_offer(self.offer(title="Développeur Python Kotlin Android", city="Tunis",
                                      contract="emploi", description="sql fastapi mongodb"), PROFILE)
        self.assertEqual(s, 100)
        s0, _ = score_offer(self.offer(title="Chauffeur poids lourd", city="Gafsa", contract="freelance"), PROFILE)
        self.assertEqual(s0, 0)

    def test_seuil_hors_empreinte_profil(self):
        self.assertEqual(profile_hash({**PROFILE, "threshold": 50}), profile_hash({**PROFILE, "threshold": 90}))
        self.assertNotEqual(profile_hash(PROFILE), profile_hash({**PROFILE, "skills": {"python": 1}}))


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.web, self.db = FakeWeb(), new_storage()

    def test_pause_entre_requetes_meme_hote(self):
        client, clock = make(self.web, self.db, delay=3.0)
        client.get("https://www.keejob.com/llms-full.txt")
        client.get("https://www.keejob.com/rss/jobs/")
        self.assertEqual(clock.slept, [3.0, 3.0])          # robots -> liste -> rss, 3 s entre chaque

    def test_robots_interdit(self):
        client, _ = make(self.web, self.db)
        with self.assertRaises(RobotsDisallowed):
            client.get("https://www.keejob.com/offres-emploi/900001/x/postuler/")
        self.assertEqual(self.web.requests_to("postuler"), 0)

    def test_reprise_apres_429_avec_retry_after(self):
        url = "https://www.keejob.com/llms-full.txt"
        self.web.errors[url] = [429]
        client, clock = make(self.web, self.db, delay=0)
        r = client.get(url)
        self.assertEqual(r.status, 200)
        self.assertIn(7.0, clock.slept)

    def test_requete_conditionnelle_304(self):
        client, _ = make(self.web, self.db)
        url = "https://www.keejob.com/llms-full.txt"
        client.get(url)
        r = client.get(url)
        self.assertTrue(r.not_modified)
        self.assertIn("If-none-match", self.web.log[-1][1])
        self.assertIn("Développeur Python", r.body)          # corps servi depuis le cache local


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.web, self.db = FakeWeb(), new_storage()
        self.ph = profile_hash(PROFILE)

    def run_once(self, max_details=40, profile=PROFILE):
        client, _ = make(self.web, self.db, delay=0)
        return run(self.db, client, profile, SOURCES, max_details)

    def test_premiere_execution(self):
        stats = self.run_once()
        k, t = stats["sources"]["keejob"], stats["sources"]["tanitjobs"]
        self.assertEqual((k["listees"], k["nouvelles"], k["details_lus"]), (4, 4, 4))
        self.assertEqual((t["listees"], t["nouvelles"], t["details_lus"]), (3, 3, 3))
        # 2 robots + 2 listes + 7 détails = 11 requêtes, pas une de plus
        self.assertEqual(stats["requetes_http_total"], 11)
        # TOUTES les offres ont un score stocké, y compris sous le seuil
        self.assertEqual(self.db.scores.count_documents({}), 7)
        doc = self.db.offers.find_one({"source": "Keejob", "source_id": "900001"})
        self.assertEqual(doc["location"], {"city": "Tunis", "country": "Tunisie"})   # format du backend
        self.assertEqual((doc["type"], doc["status"]), ("emploi", "ACTIVE"))
        self.assertEqual(doc["publication_date"].date().isoformat(), "2026-11-23")
        self.assertEqual(stats["score"]["offres_scorees"], 7)

    def test_cas_au_dessus_et_sous_le_seuil(self):
        self.run_once()
        ids = {o["_id"]: o["source_id"] for o in self.db.offers.find()}
        scores = {ids[s["offer_id"]]: s["score"] for s in self.db.scores.find()}
        self.assertGreaterEqual(scores["900001"], 70)        # dev Python à Tunis : au-dessus
        self.assertGreaterEqual(scores["900004"], 70)        # stage Android à Ariana : au-dessus
        self.assertLess(scores["900002"], 70)                # conseiller client : exclu, sous le seuil
        self.assertLess(scores["700003"], 70)                # comptable : sous le seuil
        visibles = {r["source_id"] for r in self.db.matches(self.ph, 70)}
        self.assertIn("900001", visibles)
        self.assertNotIn("900002", visibles)

    def test_changer_le_seuil_sans_reseau_ni_recalcul(self):
        self.run_once()
        a70 = len(self.db.matches(self.ph, 70))
        self.web.offline = True                              # toute requête ferait échouer le test
        a30 = len(self.db.matches(self.ph, 30))
        a90 = len(self.db.matches(self.ph, 90))
        self.assertGreater(a30, a70)
        self.assertGreaterEqual(a70, a90)
        self.assertEqual(self.db.offers_to_score(self.ph), [])   # rien à rescorer
        stats = score_all(self.db, {**PROFILE, "threshold": 40})
        self.assertEqual(stats["offres_scorees"], 0)

    def test_deuxieme_execution_sans_changement(self):
        self.run_once()
        self.web.log.clear()
        stats = self.run_once()
        self.assertEqual(len(self.web.log), 4)               # 2 robots + 2 listes en 304, aucun détail
        self.assertTrue(stats["sources"]["keejob"]["liste_304"])
        self.assertEqual(stats["score"]["offres_scorees"], 0)

    def test_offre_modifiee_seule_relue_et_rescoree(self):
        self.run_once()
        sm = self.web.pages["https://www.tanitjobs.com/sitemap.xml"]
        self.web.set_page("https://www.tanitjobs.com/sitemap.xml",
                          sm.replace("<lastmod>2026-11-19</lastmod>", "<lastmod>2026-11-23</lastmod>"))
        page = self.web.pages["__tanit_700003"]
        self.web.set_page("__tanit_700003", page.replace("Sage et Excel", "Sage, Excel et Python"))
        self.web.log.clear()
        stats = self.run_once()
        self.assertEqual(self.web.requests_to("/job/"), 1)
        self.assertEqual(self.web.requests_to("/job/700003/"), 1)
        self.assertEqual(stats["score"]["offres_scorees"], 1)

    def test_doublon_inter_sites(self):
        self.run_once()
        rows = list(self.db.offers.find({"title": "Data Analyst junior"}))
        self.assertEqual(len(rows), 2)
        self.assertEqual(sum(r["duplicate_of"] is not None for r in rows), 1)
        titles = [r["title"] for r in self.db.matches(self.ph, 0)]
        self.assertEqual(titles.count("Data Analyst junior"), 1)

    def test_plafond_de_details_et_reprise(self):
        stats = self.run_once(max_details=2)
        k = stats["sources"]["keejob"]
        self.assertEqual((k["nouvelles"], k["details_lus"], k["details_en_attente"]), (4, 2, 2))
        self.assertEqual(self.db.offers.count_documents({"detail_fetched": False}), 2)
        # les offres sans détail ont quand même un score (marqué partiel)
        partiel = self.db.offers.find_one({"detail_fetched": False})
        d = self.db.scores.find_one({"offer_id": partiel["_id"]})["details"]
        self.assertTrue(d["partiel"])
        stats2 = self.run_once(max_details=2)
        self.assertEqual(stats2["sources"]["keejob"]["details_lus"], 2)
        self.assertEqual(self.db.offers.count_documents({"detail_fetched": False}), 0)
        self.assertEqual(stats["sources"]["tanitjobs"]["details_en_attente"], 1)
        self.assertEqual(stats2["sources"]["tanitjobs"]["details_lus"], 1)
        # seules les offres complétées (2 Keejob) ou nouvelles (1 Tanitjobs) sont scorées
        self.assertEqual(stats2["score"]["offres_scorees"], 3)

    def test_offre_retiree_devient_inactive(self):
        self.run_once()
        lst = self.web.pages["https://www.keejob.com/llms-full.txt"]
        self.web.set_page("https://www.keejob.com/llms-full.txt",
                          "\n".join(l for l in lst.splitlines() if "900001" not in l))
        stats = self.run_once()
        self.assertEqual(stats["sources"]["keejob"]["inactives"], 1)
        self.assertEqual(self.db.get_offer("Keejob", "900001")["status"], "EXPIRED")   # gardée, pas supprimée
        self.assertNotIn("900001", {r["source_id"] for r in self.db.matches(self.ph, 0)})

    def test_offres_exemple_du_backend_intactes(self):
        # Une offre d'exemple de backend/seeds/offers_dev.py (sans content_hash) dans la même collection.
        self.db.offers.insert_one({"source": "TanitJobs", "source_id": "tj-1001", "title": "Stage PFE",
                                   "url": "https://www.tanitjobs.com/", "status": "ACTIVE", "type": "stage"})
        self.run_once()
        seed = self.db.get_offer("TanitJobs", "tj-1001")
        self.assertEqual(seed["status"], "ACTIVE")                  # pas désactivée par le sitemap
        self.assertEqual(self.db.scores.count_documents({"offer_id": seed["_id"]}), 0)

    def test_deduplication_par_url_normalisee(self):
        from jobscraper.normalize import finalize
        base = dict(source="Keejob", title="Dev", company="A", city="Tunis", detail_fetched=True)
        _, s1 = self.db.upsert_offer(finalize({**base, "source_id": "1",
                                               "url": "https://www.keejob.com/offres-emploi/1/dev/"}))
        # identifiant différent mais même URL une fois normalisée (paramètres de suivi, fragment, « / » final)
        _, s2 = self.db.upsert_offer(finalize({**base, "source_id": "1-bis",
                                               "url": "https://WWW.keejob.com/offres-emploi/1/dev?utm_source=x#top"}))
        self.assertEqual((s1, s2), ("new", "unchanged"))
        self.assertEqual(self.db.offers.count_documents({}), 1)

    def test_deduplication_par_url_entre_liste_et_rss(self):
        self.run_once()
        self.web.errors["https://www.keejob.com/llms-full.txt"] = [404]
        client, _ = make(self.web, self.db, delay=0)
        run(self.db, client, PROFILE, ["keejob"])
        # 900001 est dans la liste ET dans le RSS (même URL) : un seul document
        self.assertEqual(self.db.offers.count_documents({"source": "Keejob", "source_id": "900001"}), 1)
        self.assertEqual(self.db.offers.count_documents({"source": "Keejob"}), 5)   # + 900005 venue du RSS

    def test_secours_rss_si_liste_indisponible(self):
        self.web.errors["https://www.keejob.com/llms-full.txt"] = [404]
        client, _ = make(self.web, self.db, delay=0)
        stats = run(self.db, client, PROFILE, ["keejob"])
        self.assertEqual(stats["sources"]["keejob"]["listees"], 2)
        self.assertEqual(stats["sources"]["keejob"]["details_lus"], 0)   # le RSS contient déjà le texte

    def test_changement_de_profil_rescore_hors_ligne(self):
        self.run_once()
        self.web.offline = True
        new = {**PROFILE, "skills": {**PROFILE["skills"], "power bi": 3}}
        stats = score_all(self.db, new)
        self.assertEqual(stats["offres_scorees"], 7)


class EnrichTests(unittest.TestCase):
    def test_competences_extraites(self):
        from jobscraper.enrich import extract_skills
        self.assertEqual(extract_skills("Tech Lead (C# .Net / Angular)", "Git, Docker, SQL Server"),
                         ["C#", ".NET", "Angular", "Git", "Docker", "SQL", "SQL Server"])
        self.assertNotIn("C", extract_skills("Développeur C#"))
        self.assertIn("API REST", extract_skills("", "Conception d'API REST et de micro-services"))

    def test_niveau_deduit(self):
        from jobscraper.enrich import detect_level
        self.assertEqual(detect_level("Stage PFE – Développeur Android", "", None), "stage")
        self.assertEqual(detect_level("Tech Lead .NET", "", "emploi"), "confirme")
        self.assertEqual(detect_level("Développeur Python", "Expérience : 1 à 3 ans", "emploi"), "junior")
        self.assertEqual(detect_level("Développeur Python", "Expérience : 5 à 10 ans", "emploi"), "confirme")
        self.assertIsNone(detect_level("Développeur Python", "", "emploi"))


BACKEND_MATCHING = ROOT.parent / "backend" / "app" / "modules" / "offers" / "matching.py"


@unittest.skipUnless(BACKEND_MATCHING.exists(), "backend absent")
class BackendCompatTests(unittest.TestCase):
    """Les offres écrites par le scraper doivent pouvoir sortir dans le fil de l'app (POST /matches/refresh)."""

    @classmethod
    def setUpClass(cls):
        import importlib.util
        spec = importlib.util.spec_from_file_location("backend_matching", BACKEND_MATCHING)
        cls.matching = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.matching)

    def setUp(self):
        self.db = new_storage()
        client, _ = make(FakeWeb(), self.db, delay=0)
        run(self.db, client, PROFILE, SOURCES)
        self.profile = {"level": "junior", "languages": [{"name": "Français"}],
                        "skills": [{"name": n} for n in ["Python", "FastAPI", "MongoDB", "Git", "Docker", "Kotlin",
                                                         "Android", "Java", "SQL"]],
                        "experiences": [{"position": "Développeur Python"}], "educations": [], "domain": "Informatique"}
        self.prefs = {"job_types": ["stage", "emploi"], "locations": [{"city": "Tunis"}], "threshold": 70}

    def test_offre_pertinente_au_dessus_du_seuil_par_defaut(self):
        offer = self.db.get_offer("Keejob", "900001")
        self.assertEqual(offer["required_skills"][:3], ["Python", "FastAPI", "MongoDB"])
        self.assertEqual(offer["level"], "junior")
        result = self.matching.score(self.profile, self.prefs, offer)
        self.assertIsNotNone(result)
        self.assertGreaterEqual(result.score, 70)

    def test_aucune_offre_ne_fait_planter_le_matching(self):
        for offer in self.db.offers.find():
            self.matching.score(self.profile, {**self.prefs, "job_types": ["stage", "emploi", None]}, offer)

    def test_offres_anciennes_completees(self):
        self.db.offers.update_many({}, {"$unset": {"level": ""}, "$set": {"required_skills": [],
                                                                          "location.city": None}})
        self.assertEqual(self.db.backfill_matching_fields(), 7)
        offer = self.db.get_offer("Keejob", "900001")
        self.assertEqual(offer["location"]["city"], "")
        self.assertIn("Python", offer["required_skills"])


if __name__ == "__main__":
    unittest.main()
