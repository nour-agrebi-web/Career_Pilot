"""Un adaptateur par site. Chacun sait lister les offres (requête la moins coûteuse possible) et lire un détail."""
from .base import Source
from .keejob import Keejob
from .tanitjobs import Tanitjobs

SOURCES: dict[str, type[Source]] = {"keejob": Keejob, "tanitjobs": Tanitjobs}
