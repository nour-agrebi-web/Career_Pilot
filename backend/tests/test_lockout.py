from datetime import timedelta
import pytest
from app.modules.auth import lockout
from tests.conftest import login, register


def echouer(client, fois, email="nour@example.com"):
    return [login(client, email=email, password="faux") for _ in range(fois)][-1]


def debloquer(db, email="nour@example.com"):
    """Simule la fin du blocage en avançant locked_until dans le passé."""
    db.login_attempts.update_one({"_id": email}, {"$set": {"locked_until": lockout.now() - timedelta(seconds=1)}})


def retry_after(r):
    return int(r.headers["Retry-After"])


@pytest.mark.parametrize("numero, minutes", [(1, 1), (2, 3), (3, 5), (4, 5), (10, 5)])
def test_duree_de_chaque_blocage(numero, minutes):
    assert lockout.lock_duration(numero) == timedelta(minutes=minutes)


def test_deux_echecs_restent_normaux(client):
    register(client)
    assert echouer(client, 2).status_code == 401
    assert login(client).status_code == 200


def test_troisieme_echec_bloque_une_minute(client):
    register(client)
    echouer(client, 2)
    r = login(client, password="faux")
    assert r.status_code == 429
    assert 55 <= retry_after(r) <= 60
    # Même le bon mot de passe est refusé pendant le blocage.
    bon = login(client)
    assert bon.status_code == 429 and retry_after(bon) <= 60


def test_tentatives_pendant_le_blocage_non_comptees(client, db):
    register(client)
    echouer(client, 3)
    echouer(client, 5)  # refusées sans être comptées
    debloquer(db)
    assert echouer(client, 2).status_code == 401  # encore 2 essais avant le 2e blocage


def test_blocages_progressifs_1_3_5_puis_5_minutes(client, db):
    register(client)
    for minutes in (1, 3, 5, 5):
        assert echouer(client, 2).status_code == 401
        r = login(client, password="faux")
        assert r.status_code == 429
        assert minutes * 60 - 5 <= retry_after(r) <= minutes * 60
        debloquer(db)


def test_connexion_reussie_remet_le_compteur_a_zero(client, db):
    register(client)
    echouer(client, 3)
    debloquer(db)
    assert login(client).status_code == 200
    assert echouer(client, 2).status_code == 401
    r = login(client, password="faux")
    assert r.status_code == 429 and retry_after(r) <= 60  # de nouveau le 1er palier


def test_email_inconnu_bloque_aussi(client):
    assert echouer(client, 3, email="inconnu@example.com").status_code == 429


def test_blocage_propre_a_chaque_email(client):
    register(client)
    register(client, email="amine@example.com")
    echouer(client, 3)
    assert login(client, email="amine@example.com").status_code == 200
