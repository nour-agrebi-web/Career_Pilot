"""Extraction du texte d'un CV (US-2.2.1 à US-2.2.3).

- PDF : pdfplumber ; si le PDF ne contient pas de texte exploitable (CV scanné), OCR avec Tesseract.
- DOCX : python-docx (paragraphes et tableaux, souvent utilisés dans les modèles de CV).
"""
import io
import shutil
import zipfile
from dataclasses import dataclass
from app.core.config import settings

# En dessous de ce nombre de caractères, on considère que le texte n'est pas exploitable.
MIN_TEXT_LENGTH = 50


class ExtractionError(Exception):
    """Le texte n'a pas pu être extrait. `code` est renvoyé à l'application."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
        self.message = message


# Codes d'erreur (US-2.2.2 et US-2.2.3)
UNREADABLE = "UNREADABLE"   # fichier endommagé ou qui n'est pas vraiment un PDF/DOCX
NO_TEXT = "NO_TEXT"         # CV scanné que l'OCR n'a pas su lire (ou OCR indisponible)


@dataclass
class Extracted:
    text: str
    method: str  # "pdf", "docx" ou "ocr"


def looks_valid(content: bytes, ext: str) -> bool:
    """Vérifie la signature du fichier : un .pdf commence par %PDF, un .docx est une archive ZIP."""
    if ext == ".pdf":
        return content[:1024].lstrip().startswith(b"%PDF")
    return content.startswith(b"PK")


def extract_text(content: bytes, ext: str) -> Extracted:
    if not looks_valid(content, ext):
        raise ExtractionError(UNREADABLE, "Ce fichier est endommagé ou n'est pas un vrai fichier PDF ou DOCX.")
    if ext == ".docx":
        return Extracted(_docx_text(content), "docx")
    text = _pdf_text(content)
    if len(text.strip()) >= MIN_TEXT_LENGTH:
        return Extracted(text, "pdf")
    # US-2.2.2 : PDF sans texte exploitable, c'est probablement un scan : OCR.
    text = _ocr_pdf(content)
    if len(text.strip()) < MIN_TEXT_LENGTH:
        raise ExtractionError(NO_TEXT, "Le texte de ce CV scanné n'a pas pu être lu.")
    return Extracted(text, "ocr")


def _docx_text(content: bytes) -> str:
    from docx import Document

    try:
        document = Document(io.BytesIO(content))
    except (zipfile.BadZipFile, KeyError, ValueError) as e:
        raise ExtractionError(UNREADABLE, "Ce fichier DOCX est endommagé.") from e
    lines = [p.text for p in document.paragraphs]
    for table in document.tables:
        for row in table.rows:
            seen = []
            for cell in row.cells:  # les cellules fusionnées apparaissent plusieurs fois
                if cell.text not in seen:
                    seen.append(cell.text)
            lines.extend(seen)
    text = "\n".join(line for line in lines if line.strip())
    if len(text.strip()) < MIN_TEXT_LENGTH:
        raise ExtractionError(NO_TEXT, "Ce fichier DOCX ne contient presque pas de texte.")
    return text


def _pdf_text(content: bytes) -> str:
    import pdfplumber

    try:
        with pdfplumber.open(io.BytesIO(content)) as pdf:
            return "\n".join(page.extract_text() or "" for page in pdf.pages)
    except Exception as e:  # pdfminer lève des erreurs très variées sur un fichier abîmé
        raise ExtractionError(UNREADABLE, "Ce fichier PDF est endommagé.") from e


def ocr_available() -> bool:
    return bool(settings.tesseract_cmd) or shutil.which("tesseract") is not None


def _ocr_pdf(content: bytes) -> str:
    if not ocr_available():
        return ""
    import pypdfium2
    import pytesseract

    if settings.tesseract_cmd:
        pytesseract.pytesseract.tesseract_cmd = settings.tesseract_cmd
    try:
        pdf = pypdfium2.PdfDocument(content)
        texts = []
        for index in range(min(len(pdf), 5)):  # un CV fait rarement plus de 5 pages
            image = pdf[index].render(scale=300 / 72).to_pil()
            texts.append(pytesseract.image_to_string(image, lang=_ocr_languages()))
        return "\n".join(texts)
    except Exception:
        return ""


def _ocr_languages() -> str:
    """Français + anglais (+ arabe) selon les langues installées avec Tesseract."""
    import pytesseract

    try:
        installed = set(pytesseract.get_languages())
    except Exception:
        return "eng"
    wanted = [lang for lang in ("fra", "eng", "ara") if lang in installed]
    return "+".join(wanted) or "eng"
