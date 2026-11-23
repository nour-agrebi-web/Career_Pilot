from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Listing:
    items: list[dict] = field(default_factory=list)
    complete: bool = False       # True : la liste contient TOUTES les offres actives (permet de marquer les absentes)
    not_modified: bool = False   # True : le serveur a répondu 304, rien n'a changé depuis la dernière exécution
    requests: int = 0


class Source:
    name = "base"
    label = "Base"

    def list_offers(self, client) -> Listing:
        """Liste des offres. Chaque élément a au minimum source_id, url ; `insertable` s'il a un titre."""
        raise NotImplementedError

    def detail_url(self, item: dict) -> str:
        return item["url"]

    def parse_detail(self, item: dict, body: str) -> dict | None:
        raise NotImplementedError
