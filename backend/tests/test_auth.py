from app.modules.auth.models import Role
from tests.conftest import login, register


def bearer(token):
    return {"Authorization": f"Bearer {token}"}


# US-1.1.1 / US-1.1.2
def test_inscription_cree_un_compte(client):
    r = register(client)
    assert r.status_code == 201
    assert r.json()["email"] == "nour@example.com"


def test_inscription_refusee_sans_consentement(client):
    assert register(client, consent=False).status_code == 400


def test_inscription_refusee_si_email_deja_utilise(client):
    register(client)
    assert register(client, email="NOUR@example.com").status_code == 409


def test_mot_de_passe_hache(client, db):
    register(client)
    user = db.users.find_one({"email": "nour@example.com"})
    assert user["password_hash"] != "motdepasse123"
    assert user["consent"] is True and user["consent_at"] is not None


# US-1.2.1
def test_echec_de_connexion_message_unique(client):
    register(client)
    mauvais_mdp = login(client, password="faux")
    email_inconnu = login(client, email="inconnu@example.com")
    assert mauvais_mdp.status_code == email_inconnu.status_code == 401
    assert mauvais_mdp.json() == email_inconnu.json()


# US-1.2.2
def test_connexion_puis_refresh_avec_rotation(client):
    register(client)
    tokens = login(client).json()
    assert client.get("/users/me", headers=bearer(tokens["access_token"])).status_code == 200

    nouveaux = client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert nouveaux.status_code == 200
    # l'ancien refresh token ne sert qu'une fois
    assert client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]}).status_code == 401


def test_logout_revoque_le_refresh_token(client):
    register(client)
    tokens = login(client).json()
    assert client.post("/auth/logout", json={"refresh_token": tokens["refresh_token"]}).status_code == 204
    assert client.post("/auth/refresh", json={"refresh_token": tokens["refresh_token"]}).status_code == 401


def test_refresh_token_refuse_comme_jeton_d_acces(client):
    register(client)
    tokens = login(client).json()
    assert client.get("/users/me", headers=bearer(tokens["refresh_token"])).status_code == 401


# US-1.3.2
def test_admin_reserve_aux_administrateurs(client, db):
    register(client)
    tokens = login(client).json()
    assert client.get("/admin/stats", headers=bearer(tokens["access_token"])).status_code == 403

    db.users.update_one({"email": "nour@example.com"}, {"$set": {"role": Role.ADMIN.value}})
    r = client.get("/admin/stats", headers=bearer(tokens["access_token"]))
    assert r.status_code == 200 and r.json() == {"users": 1}


def test_jeton_avec_identifiant_invalide_refuse(client):
    assert client.get("/users/me", headers=bearer("pas-un-jwt")).status_code == 401
    assert client.post("/auth/refresh", json={"refresh_token": "pas-un-jwt"}).status_code == 401
