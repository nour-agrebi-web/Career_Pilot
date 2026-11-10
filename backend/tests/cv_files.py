"""Fabrique de vrais fichiers PDF et DOCX pour les tests (sans dépendance supplémentaire)."""
import io
from docx import Document

CV_TEXT = """Nour AGREBI
Développeuse mobile Android
nour.agrebi@example.com · +216 22 123 456
12 rue de la Liberté, Radès
Profil
Étudiante en licence DSI, je cherche un stage de fin d'études en développement mobile.
Compétences
Kotlin, Jetpack Compose, Python, FastAPI
MongoDB · Git
Expériences professionnelles
07/2025 - 08/2025 Stagiaire développeuse - Nexa Digital
Application Android de suivi de colis.
Formation
2024 - 2027 Licence Développement des Systèmes d'Information - ISET Radès
2023 Baccalauréat Mathématiques - Lycée Pilote de Ben Arous
Langues
Arabe : langue maternelle
Français : courant
Anglais (B2)
Centres d'intérêt
Robotique, échecs"""


def make_docx(text: str = CV_TEXT) -> bytes:
    document = Document()
    for line in text.splitlines():
        document.add_paragraph(line)
    buffer = io.BytesIO()
    document.save(buffer)
    return buffer.getvalue()


def make_pdf(text: str = CV_TEXT) -> bytes:
    """PDF d'une page avec du texte (police Helvetica, encodage WinAnsi pour les accents)."""
    def escape(line: str) -> str:
        return line.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    lines = [escape(line) for line in text.replace("·", "-").splitlines()]
    stream = "BT /F1 10 Tf 12 TL 50 800 Td " + " ".join(f"({line}) Tj T*" for line in lines) + " ET"
    stream_bytes = stream.encode("cp1252")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(stream_bytes)).encode() + b" >>\nstream\n" + stream_bytes + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>",
    ]
    out = bytearray(b"%PDF-1.4\n")
    offsets = []
    for number, body in enumerate(objects, start=1):
        offsets.append(len(out))
        out += f"{number} 0 obj\n".encode() + body + b"\nendobj\n"
    xref = len(out)
    out += f"xref\n0 {len(objects) + 1}\n0000000000 65535 f \n".encode()
    for offset in offsets:
        out += f"{offset:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
    return bytes(out)


def make_blank_pdf() -> bytes:
    """PDF sans texte, comme un CV scanné."""
    return make_pdf("")
