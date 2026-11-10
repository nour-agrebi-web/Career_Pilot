from datetime import timedelta
from app.modules.auth import lockout
from tests.conftest import login, register


def echouer(client, fois, email="nour@example.com"):
    return [login(client, email=email, password="faux") for _ in range(fois)][-1]


def debloquer(db, email="nour@example.com"):
    """Simule la fin du blocage en avançant locked_until dans le passé."""
    db.login_attempts.update_one({"_id": email}, {"$set": {"locked_until": lockout.now() - timedelta(seconds=1)}})


def test_trois_echecs_restent_normaux(client):
    register(client)
    assert echouer(client, 3).status_code == 401
    assert login(client).status_code == 200


def test_quatrieme_echec_bloque_trois_minutes(client):
    register(client)
    echouer(client, 3)
    r = login(client, password="faux")
    assert r.status_code == 429
    assert 170 <= int(r.headers["Retry-After"]) <= 180
    # Même le bon mot de passe est refusé pendant le blocage.
    bon = login(client)
    assert bon.status_code == 429 and int(bon.headers["Retry-After"]) <= 180


def test_sixieme_echec_bloque_quinze_minutes(client, db):
    register(client)
    echouer(client, 4)
    debloquer(db)
    assert login(client, password="faux").status_code == 429  # 5e échec : encore 3 min
    debloquer(db)
    r = login(client, password="faux")  # 6e échec
    assert r.status_code == 429
    assert 15 * 60 - 10 <= int(r.headers["Retry-After"]) <= 15 * 60


def test_connexion_reussie_remet_le_compteur_a_zero(client, db):
    register(client)
    echouer(client, 4)
    debloquer(db)
    assert login(client).status_code == 200
    assert echouer(client, 3).status_code == 401  # de nouveau 3 essais normaux


def test_email_inconnu_bloque_aussi(client):
    assert echouer(client, 4, email="inconnu@example.com").status_code == 429


def test_blocage_propre_a_chaque_email(client):
    register(client)
    register(client, email="amine@example.com")
    echouer(client, 4)
    assert login(client, email="amine@example.com").status_code == 200
