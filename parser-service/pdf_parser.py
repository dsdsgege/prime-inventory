import io
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from datetime import datetime
from typing import Dict, Any, List, Optional, Tuple, Union

try:
    import pdfplumber
except ImportError:
    pdfplumber = None


class UnsupportedInvoiceFormatError(ValueError):
    """Raised when an uploaded PDF does not match any supported invoice template or fails validation."""
    pass


# Regular expression for NMHH 4x4 data wipe codes (e.g. USWA-CFXS-ZGWF-YFD5)
WIPE_CODE_REGEX = re.compile(r'\b[A-Z0-9]{4}(?:-[A-Z0-9]{4}){3}\b')

# Non-physical units that indicate time-based or service-based billing rather than physical stock
NON_PHYSICAL_UNITS = {
    "hó", "ho", "hónap", "honap",
    "óra", "ora",
    "nap", "hét", "het", "év", "ev",
    "alkalom", "alk", "alk.",
    "szolgáltatás", "szolgaltatas"
}

# Comprehensive pool of non-physical service, shipping, IT, rent, utility, accounting, and overhead expense keywords
SERVICE_BLACKLIST = [
    # Shipping, courier, logistics & packaging
    "csomagküldés", "csomagkuldes",
    "csomagszállítás", "csomagszallitas",
    "belföldi csomagszállítás", "belfoldi csomagszallitas",
    "domestic delivery",
    "gls", "dpd", "mpl", "foxpost", "packeta", "dhl", "ups", "fedex", "sameday", "express one", "sprinter",
    "szállítás", "szallitas",
    "szállítási díj", "szallitasi dij",
    "szállítási költség", "szallitasi koltseg",
    "kiszállítás", "kiszallitas",
    "kiszállási díj", "kiszallasi dij",
    "házhozszállítás", "hazhozszallitas",
    "fuvardíj", "fuvardij",
    "fuvarozás", "fuvarozas",
    "postaköltség", "postakoltseg",
    "postai díj", "postai dij",
    "utánvét", "utanvet",
    "utánvételi díj", "utanveteli dij",
    "futár", "futar",
    "futárszolgálat", "futarszolgalat",
    "úthasználat", "uthasznalat",
    "útdíj", "utdij",
    "transport fee", "use of road",
    "csomagolási díj", "csomagolasi dij",
    "csomagolási költség", "csomagolasi koltseg",
    "raklap", "logisztikai díj", "logisztikai dij",

    # Banking, card collection & payment processing
    "bankkártyás beszedés", "bankkartyas beszedes",
    "bank card service",
    "tranzakciós díj", "tranzakcios dij",
    "jutalék", "jutalek",
    "kezelési költség", "kezelesi koltseg",
    "kezelési díj", "kezelesi dij",
    "adminisztrációs díj", "adminisztracios dij",
    "adminisztráció", "adminisztracio",
    "pénzkezelési díj", "penzkezelesi dij",
    "utalási díj", "utalasi dij",
    "banki költség", "banki koltseg",
    "kerekítés", "kerekites",
    "késedelmi kamat", "kesedelmi kamat",
    "eljárási díj", "eljarasi dij",
    "rendelkezésre állási díj", "rendelkezesre allasi dij",

    # Web, IT, software, hosting & telecom
    "karbantartás", "karbantartas",
    "weboldal", "honlap", "weblap", "webshop",
    "üzemeltetés", "uzemeltetes",
    "tárhely", "tarhely", "domain", "hosting", "szerver", "felhő", "cloud",
    "szoftver", "licenc", "license",
    "előfizetés", "elofizetes",
    "havidíj", "havidij",
    "éves díj", "eves dij",
    "rendszerfelügyelet", "rendszerfelugyelet",
    "fejlesztés", "fejlesztes",
    "programozás", "programozas",
    "it szolgáltatás", "it szolgaltatas",
    "számlázó program", "szamlazo program",
    "internet", "telefondíj", "telefondij", "telekommunikáció",

    # Rent, office, utilities & facility expenses
    "bérleti díj", "berleti dij",
    "bérlet", "berlet",
    "irodabérlet", "irodaberlet",
    "üzlethelyiség", "uzlethelyiseg",
    "albérlet", "alberlet",
    "közös költség", "kozos koltseg",
    "rezsi", "közüzemi", "kozuzemi",
    "villany", "áramdíj", "aramdij", "elektromos energia",
    "gázdíj", "gazdij", "földgáz",
    "vízdíj", "vizdij", "csatornadíj",
    "fűtés", "futes", "távfűtés",
    "hulladékszállítás", "szemétszállítás",
    "takarítás", "takaritas",
    "őrzés-védés", "riasztó", "biztonsági szolgálat",
    "parkolási díj", "parkolasi dij", "parkolás",

    # Accounting, legal, consulting & HR
    "könyvelés", "konyveles",
    "könyvelési díj", "konyvelesi dij",
    "számviteli", "szamviteli",
    "adótanácsadás", "adotanacsadas",
    "bérszámfejtés", "berszamfejtes",
    "könyvvizsgálat", "konyvvizsgalat",
    "ügyvédi díj", "ugyvedi dij",
    "jogi szolgáltatás", "jogi szolgaltatas",
    "jogdíj", "jogdij",
    "közjegyzői díj", "kozjegyzo",
    "tanácsadás", "tanacsadas",
    "konzultáció", "konzultacio",
    "oktatás", "oktatas", "képzés", "kepzes",

    # Marketing & advertising
    "hirdetés", "hirdetes",
    "reklám", "reklam",
    "marketing", "google ads", "facebook ads", "meta ads", "tiktok ads",
    "seo", "keresőoptimalizálás",
    "kampány", "kampany",
    "grafikai tervezés", "grafikai munka",

    # Service labor, insurance & warranty fees
    "munkadíj", "munkadij",
    "szervizdíj", "szervizdij",
    "javítási díj", "javitasi dij",
    "bevizsgálási díj", "bevizsgalasi dij",
    "szolgáltatási díj", "szolgaltatasi dij",
    "közvetített szolgáltatás", "kozvetitett szolgaltatas",
    "biztosítás", "biztositas",
    "garancia kiterjesztés", "kiterjesztett garancia"
]

# ==========================================
# Regex patterns for Számlázz.hu
# ==========================================
INVOICE_NUM_REGEX = re.compile(r'Sorszám:\s*([A-Z0-9\-/]+)', re.IGNORECASE)
PAYMENT_METHOD_REGEX = re.compile(
    r'Fizetési\s*mód:\s*([^\n\r]+?)(?=\s+Teljesítés|\s+Kiállítás|\s+Adószám|\n|$)',
    re.IGNORECASE
)
DELIVERY_DATE_REGEX = re.compile(r'Teljesítés\s*dátuma:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)
ISSUE_DATE_REGEX = re.compile(r'Kiállítás\s*dátuma:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)
DUE_DATE_REGEX = re.compile(r'Fizetési\s*határid\S*:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)

TAX_NUM_REGEX = re.compile(r'Adószám:\s*(\d{8}-\d-\d{2})(?:,\s*([A-Z]{2}\d+))?', re.IGNORECASE)
BANK_ACC_REGEX = re.compile(r'(?:Bankszámlaszám:\s*)?(\b\d{8}-\d{8}(?:-\d{8})?\b|\b\d{24}\b)', re.IGNORECASE)

SZAMLAZZ_ITEM_ROW_REGEX = re.compile(
    r"^(.+?)\s+(\d+)\s*(db|DB|db\.|hó|óra|alkalom|darab|szett|csomag|év|nap)\s+([\d\s]+?)\s+(K\.AFA|K\.ÁFA|AAM|TAM|FAD|\d+%|[A-Za-z0-9\.\%]+)\s+([\d\s]+)$"
)

# ==========================================
# Regex patterns for Billingo
# ==========================================
BILLINGO_INVOICE_NUM_REGEX = re.compile(
    r'(?:Elektronikus\s+számla|Számla)\s*\n\s*([A-Z0-9]+\s*/\s*[A-Z0-9\-]+|[A-Z0-9\-/]{5,})',
    re.IGNORECASE
)
BILLINGO_ISSUE_DATE_REGEX = re.compile(r'SZÁMLA\s*KELTE\s*:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)
BILLINGO_DELIVERY_DATE_REGEX = re.compile(r'TELJESÍTÉS\s*KELTE\s*:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)
BILLINGO_DUE_DATE_REGEX = re.compile(r'FIZETÉSI\s*HATÁRID\S*\s*:\s*(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)', re.IGNORECASE)
BILLINGO_PAYMENT_REGEX = re.compile(
    r'FIZETÉSI\s*MÓD\s*:\s*([^\n\r]+?)(?=\s+TELJESÍTÉS|\s+SZÁMLA|\s+FIZETÉSI|\n|$)',
    re.IGNORECASE
)
BILLINGO_EU_TAX_REGEX = re.compile(r'KÖZÖSSÉGI\s*ADÓSZÁM\s*:\s*([A-Z]{2}\d+)', re.IGNORECASE)

BILLINGO_ITEM_ROW_REGEX = re.compile(
    r"^(?:(\d+)\s+)?(.+?)\s+(\d+)\s*(db|DB|db\.|hó|óra|alkalom|darab|szett|csomag|év|nap)\s+([\d\s]+?)\s*(?:Ft|HUF)\s+([\d\s]+?)\s*(?:Ft|HUF)\s+(AAM|TAM|FAD|K\.AFA|K\.ÁFA|\d+\s*%|0%|[A-Za-z0-9\.\%]+)\s+([\d\s]+?)\s*(?:Ft|HUF)?$"
)

# ==========================================
# Regex patterns for JMStock / QrSzla (Extreme GSM)
# ==========================================
JMSTOCK_META_ROW_REGEX = re.compile(
    r"^([A-Z0-9/\-]+)\s+(.+?)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)\s+([A-Z]{3})$"
)
JMSTOCK_ITEM_ROW_REGEX = re.compile(
    r"^(\d+)\s+(.+?)\s+(db|DB|db\.|hó|óra|alkalom|darab|szett|csomag)\s+(\d+)\s+([\d\s]+)$"
)
JMSTOCK_REF_VAT_ROW_REGEX = re.compile(
    r"^([A-Za-z0-9\-_/.]+)\s+(\d{1,2}|AAM|TAM|FAD|K\.AFA)$"
)

# ==========================================
# Regex patterns for GLS GOLD
# ==========================================
GLS_META_ROW_REGEX = re.compile(
    r"^([A-Z0-9/\-]+)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)\s+(.+?)\s+(\d{4}\.\s*\d{2}\.\s*\d{2}\.?)$"
)
GLS_ITEM_ROW_REGEX = re.compile(
    r"^(?:(.+?)\s+)?(pcs|db|DB)\s+(\d+(?:[.,]\d+)?)\s+([\d\s]+)\s+(27|18|5|0)\s+([\d\s]+)$"
)


# ==========================================
# Fallback PDF Page Wrapper (using pdftotext -bbox-layout when pdfplumber is absent)
# ==========================================
class _BboxPage:
    def __init__(self, width: float, height: float, words: List[Tuple[float, float, float, float, str]]):
        self.width = width
        self.height = height
        self._words = words

    def extract_text(self) -> str:
        if not self._words:
            return ""
        sorted_words = sorted(self._words, key=lambda w: (w[1], w[0]))
        lines: List[List[Tuple[float, float, float, float, str]]] = []
        for w in sorted_words:
            if not lines:
                lines.append([w])
            else:
                prev_y = lines[-1][0][1]
                if abs(w[1] - prev_y) <= 3.5:
                    lines[-1].append(w)
                else:
                    lines.append([w])
        out_lines = []
        for line_words in lines:
            line_words.sort(key=lambda w: w[0])
            out_lines.append(" ".join(w[4] for w in line_words))
        return "\n".join(out_lines)

    def crop(self, bbox: Tuple[float, float, float, float]) -> "_BboxPage":
        x0, top, x1, bottom = bbox
        cropped = [
            w for w in self._words
            if w[0] >= x0 - 1.0 and w[2] <= x1 + 1.0 and w[1] >= top - 1.0 and w[3] <= bottom + 1.0
        ]
        return _BboxPage(x1 - x0, bottom - top, cropped)


class _BboxPdf:
    def __init__(self, file_source: Union[str, bytes, io.BytesIO]):
        self.pages: List[_BboxPage] = []
        self._tmp_path: Optional[str] = None
        if isinstance(file_source, (bytes, io.BytesIO)):
            raw = file_source if isinstance(file_source, bytes) else file_source.getvalue()
            tf = tempfile.NamedTemporaryFile(suffix=".pdf", delete=False)
            tf.write(raw)
            tf.close()
            self._tmp_path = tf.name
            pdf_path = self._tmp_path
        else:
            pdf_path = str(file_source)

        try:
            proc = subprocess.run(
                ["pdftotext", "-bbox-layout", pdf_path, "-"],
                capture_output=True,
                text=True,
                check=True
            )
            xml_text = proc.stdout
            root = ET.fromstring(xml_text)
            ns = {"x": "http://www.w3.org/1999/xhtml"}
            for page_el in root.findall(".//x:page", ns) or root.findall(".//page"):
                w = float(page_el.attrib.get("width", "595"))
                h = float(page_el.attrib.get("height", "842"))
                words = []
                word_els = page_el.findall(".//x:word", ns) or page_el.findall(".//word")
                for w_el in word_els:
                    txt = (w_el.text or "").strip()
                    if txt:
                        words.append((
                            float(w_el.attrib["xMin"]),
                            float(w_el.attrib["yMin"]),
                            float(w_el.attrib["xMax"]),
                            float(w_el.attrib["yMax"]),
                            txt
                        ))
                self.pages.append(_BboxPage(w, h, words))
        finally:
            if self._tmp_path:
                import os
                try:
                    os.unlink(self._tmp_path)
                except OSError:
                    pass

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        pass


def _open_pdf(file_source: Union[str, bytes, io.BytesIO]):
    if pdfplumber is not None:
        pdf_obj = io.BytesIO(file_source) if isinstance(file_source, bytes) else file_source
        return pdfplumber.open(pdf_obj)
    return _BboxPdf(file_source)


# ==========================================
# Shared Utility Functions
# ==========================================
def parse_date(date_str: Optional[str]) -> Optional[str]:
    """Parses date string (YYYY.MM.DD or YYYY. MM. DD.) and returns ISO formatted string YYYY-MM-DD."""
    if not date_str:
        return None
    cleaned = re.sub(r'\s+', '', date_str).strip().rstrip(".")
    try:
        dt = datetime.strptime(cleaned, "%Y.%m.%d").date()
        return dt.isoformat()
    except Exception:
        return None


def parse_integer(val: Any) -> int:
    if val is None:
        return 0
    if isinstance(val, (int, float)):
        return int(val)
    cleaned = re.sub(r'[^\d\-]', '', str(val))
    try:
        return int(cleaned) if cleaned else 0
    except ValueError:
        return 0


def format_huf(amount: int) -> str:
    return f"{amount:,}".replace(",", " ")


def format_expense_entry(name: str, net_price: int, gross_price: int) -> str:
    clean_title = clean_product_name(name) or name.strip()
    return f"{clean_title} (Nettó: {format_huf(net_price)} Ft | Bruttó: {format_huf(gross_price)} Ft)"


def split_two_numbers(num_str: str, expected_relation) -> Tuple[int, int]:
    """
    Splits a string containing two space-formatted integers (e.g. '205 000 410 000' or '118 110 118 110' or '0 410 000')
    into (first_int, second_int) using the invoice mathematical relation (e.g. unit * qty == net, net + vat == gross).
    """
    tokens = [re.sub(r'[^\d\-]', '', t) for t in num_str.strip().split()]
    tokens = [t for t in tokens if t]
    if not tokens:
        return 0, 0
    if len(tokens) == 1:
        v = int(tokens[0])
        return v, v
    if len(tokens) == 2:
        return int(tokens[0]), int(tokens[1])

    best_pair = (int("".join(tokens[:-1])), int(tokens[-1]))
    best_diff = float("inf")
    for i in range(1, len(tokens)):
        left = int("".join(tokens[:i]))
        right = int("".join(tokens[i:]))
        diff = expected_relation(left, right)
        if diff < best_diff:
            best_diff = diff
            best_pair = (left, right)
    return best_pair


def split_four_numbers(num_str: str, qty: int) -> Tuple[int, int, int, int]:
    """
    Splits a string containing 4 space-formatted integers (unit_net, total_net, total_vat, total_gross)
    using invoice invariants: total_net + total_vat == total_gross and unit_net * qty ≈ total_net.
    """
    tokens = [re.sub(r'[^\d\-]', '', t) for t in num_str.strip().split()]
    tokens = [t for t in tokens if t]
    n_tok = len(tokens)
    if n_tok < 4:
        vals = [int(t) for t in tokens] + [0] * (4 - n_tok)
        return vals[0], vals[1], vals[2], vals[3]
    if n_tok == 4:
        return int(tokens[0]), int(tokens[1]), int(tokens[2]), int(tokens[3])

    best = (int(tokens[0]), int(tokens[1]), int(tokens[2]), int("".join(tokens[3:])))
    best_score = float("inf")
    for i in range(1, n_tok - 2):
        for j in range(i + 1, n_tok - 1):
            for k in range(j + 1, n_tok):
                u = int("".join(tokens[:i]))
                n = int("".join(tokens[i:j]))
                v = int("".join(tokens[j:k]))
                g = int("".join(tokens[k:]))
                gross_err = abs((n + v) - g)
                net_err = abs(u * qty - n)
                score = gross_err * 10000 + net_err
                if score < best_score:
                    best_score = score
                    best = (u, n, v, g)
    return best


def beautify_name(name: str) -> str:
    """Restores readable spaces in case words were extracted without whitespace."""
    if not name:
        return ""
    s = name
    s = re.sub(r"(PLANET)(MOBILE)", r"\1 \2", s)
    s = re.sub(r"(Samsung)(Galaxy)", r"\1 \2", s)
    s = re.sub(r"(Apple)(iPhone)", r"\1 \2", s)
    s = re.sub(r"(iPhone)(\d+)", r"\1 \2", s)
    s = re.sub(r"(Galaxy)([A-Z]\d+)", r"\1 \2", s)
    s = re.sub(r"(Galaxy)(Tab)", r"\1 \2", s)
    s = re.sub(r"(Tab)([A-Z]\d+)", r"\1 \2", s)
    s = re.sub(r"([a-zA-Z0-9])(Pro|fe|Plus|Max|Ultra|Kft|Bt|Zrt)", r"\1 \2", s)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def is_boilerplate_or_wipe_line(line: str) -> bool:
    """Returns True if the line is legal/tax boilerplate or an NMHH wipe code rather than a product name."""
    lower = line.lower().strip()
    if not lower:
        return True
    if WIPE_CODE_REGEX.search(line):
        return True
    if any(kw in lower for kw in [
        "különbözet",
        "kulonbozet",
        "szabályozás",
        "szabalyozas",
        "használt",
        "hasznalt",
        "adat",
        "törl",
        "torl"
    ]):
        return True
    return False


def clean_product_name(raw_name: str) -> str:
    """Removes boilerplate legal phrases and wipe codes, restoring clean product title."""
    if not raw_name:
        return ""
    lines = [l.strip() for l in raw_name.splitlines() if l.strip()]
    product_lines = []
    for line in lines:
        if is_boilerplate_or_wipe_line(line):
            continue
        product_lines.append(line)

    clean_str = " ".join(product_lines).strip() if product_lines else raw_name
    clean_str = re.sub(r'Különbözet[^\n\r]*', '', clean_str, flags=re.IGNORECASE)
    clean_str = re.sub(r'-használt[^\n\r]*', '', clean_str, flags=re.IGNORECASE)
    clean_str = re.sub(r'adat\s*t[^\s:]*:?', '', clean_str, flags=re.IGNORECASE)
    clean_str = WIPE_CODE_REGEX.sub('', clean_str)
    return beautify_name(clean_str)


def is_service_item(text: str, unit: Optional[str] = None) -> bool:
    """Checks if an invoice item row is a non-physical service, shipping fee, or overhead expense."""
    if unit and unit.strip().lower().rstrip(".") in NON_PHYSICAL_UNITS:
        return True
    lower_text = text.lower()
    return any(keyword in lower_text for keyword in SERVICE_BLACKLIST)


def _extract_left_column_text(page, height_ratio: float = 0.48) -> str:
    """Extracts text from the left half of the page header to isolate Supplier from Buyer in 2-column layouts."""
    try:
        cropped = page.crop((0, 0, page.width * 0.50, page.height * height_ratio))
        return cropped.extract_text() or ""
    except Exception:
        return page.extract_text() or ""


# ==========================================
# Format Detection
# ==========================================
def detect_invoice_format(full_text: str, page1_text: str) -> Optional[str]:
    """
    Identifies the invoice template format.
    Returns one of: 'SZAMLAZZ_HU', 'BILLINGO', 'JMSTOCK', 'GLS_GOLD', or None if unknown.
    """
    lower_all = full_text.lower()
    lower_p1 = page1_text.lower()

    if "gls gold" in lower_all or (
        "szállító/supplier" in lower_p1 and "gls general logistics" in lower_p1
    ):
        return "GLS_GOLD"

    if "billingo" in lower_all or (
        "eladó" in lower_p1 and "számla kelte" in lower_p1 and "nettó egységár" in lower_p1
    ):
        return "BILLINGO"

    if "jmstock" in lower_all or "qrszla" in lower_all or (
        "számlakibocsátó" in lower_p1 and "mértékegység" in lower_p1 and "net.egységár" in lower_p1
    ):
        return "JMSTOCK"

    if "számlázz.hu" in lower_all or "szamlazz.hu" in lower_all or (
        "sorszám:" in lower_p1 and "megnevezés" in lower_p1 and ("menny." in lower_p1 or "egységár" in lower_p1)
    ):
        return "SZAMLAZZ_HU"

    return None


# ==========================================
# Parser 1: Számlázz.hu
# ==========================================
def _parse_szamlazz_hu(pdf, page1_text: str) -> Dict[str, Any]:
    vevo_split = re.split(r'\bVEV\S*:', page1_text, flags=re.IGNORECASE)
    supplier_part = vevo_split[0]

    supplier_tax = ""
    supplier_eu_tax = None
    tax_match = TAX_NUM_REGEX.search(supplier_part)
    if tax_match:
        supplier_tax = tax_match.group(1).strip()
        if tax_match.group(2):
            supplier_eu_tax = tax_match.group(2).strip()

    bank_match = BANK_ACC_REGEX.search(supplier_part)
    supplier_bank = bank_match.group(1).strip() if bank_match else None

    supplier_name = "Ismeretlen Beszállító"
    supplier_address = ""
    supplier_lines = [l.strip() for l in supplier_part.splitlines() if l.strip()]

    for line in supplier_lines:
        m = re.search(r'^(.*?(?:Kft\.?|Bt\.?|Zrt\.?|Nyrt\.?|Egyéni vállalkozó|\bev\.))', line, flags=re.IGNORECASE)
        if m:
            supplier_name = beautify_name(m.group(1).strip())
            break

    if supplier_name == "Ismeretlen Beszállító" and supplier_lines:
        first_line = re.sub(r'\bSZÁMLA\b.*$', '', supplier_lines[0]).strip()
        if first_line:
            supplier_name = beautify_name(first_line)

    addr_parts = []
    for line in supplier_lines:
        if line.startswith(supplier_name) or "SZÁMLA" in line or "Adószám:" in line or "Bank" in line or "Sorszám:" in line:
            continue
        clean_line = re.sub(r'(?:Sorszám:|Fizetési|Teljesítés|Kiállítás|Bank|Bankszámlaszám).*$', '', line).strip()
        clean_line = re.sub(r'\b\d{8}-\d{8}(?:-\d{8})?\b', '', clean_line).strip()
        if clean_line:
            addr_parts.append(beautify_name(clean_line))

    if addr_parts:
        supplier_address = ", ".join(addr_parts)

    invoice_num_match = INVOICE_NUM_REGEX.search(page1_text)
    invoice_number = invoice_num_match.group(1).strip() if invoice_num_match else "UNKNOWN-INVOICE"

    pay_method_match = PAYMENT_METHOD_REGEX.search(page1_text)
    payment_method = pay_method_match.group(1).strip() if pay_method_match else None

    delivery_date = parse_date(DELIVERY_DATE_REGEX.search(page1_text).group(1)) if DELIVERY_DATE_REGEX.search(page1_text) else None
    issue_date = parse_date(ISSUE_DATE_REGEX.search(page1_text).group(1)) if ISSUE_DATE_REGEX.search(page1_text) else None
    due_date = parse_date(DUE_DATE_REGEX.search(page1_text).group(1)) if DUE_DATE_REGEX.search(page1_text) else None

    parsed_items: List[Dict[str, Any]] = []
    filtered_services: List[str] = []
    last_item: Optional[Dict[str, Any]] = None
    pending_lines: List[str] = []

    total_net_all = 0
    total_vat_all = 0
    total_gross_all = 0

    for page in pdf.pages:
        page_text = page.extract_text() or ""
        lines = page_text.splitlines()
        in_table_section = False

        for line in lines:
            line_str = line.strip()
            if not line_str:
                continue

            if "Megnevezés" in line_str and ("Menny" in line_str or "Egységár" in line_str):
                in_table_section = True
                continue

            if in_table_section:
                if any(stop_phrase in line_str for stop_phrase in [
                    "(folytatás",
                    "Összesen:",
                    "Levelezés:",
                    "Abizonylatot",
                    "A bizonylatot"
                ]):
                    in_table_section = False
                    continue

                row_match = SZAMLAZZ_ITEM_ROW_REGEX.match(line_str)
                if row_match:
                    raw_name = row_match.group(1).strip()
                    quantity = int(row_match.group(2))
                    unit = row_match.group(3)
                    pre_vat = row_match.group(4)
                    vat_rate = row_match.group(5).strip()
                    post_vat = row_match.group(6)

                    unit_price, net_price = split_two_numbers(
                        pre_vat, lambda u, n: abs(u * quantity - n)
                    )
                    vat_amount, gross_price = split_two_numbers(
                        post_vat, lambda v, g: abs((net_price + v) - g)
                    )

                    combined_name_lines = pending_lines + [raw_name]
                    combined_raw_desc_lines = pending_lines + [line_str]
                    pending_lines = []

                    full_name_source = "\n".join(combined_name_lines)

                    if is_service_item(full_name_source, unit):
                        filtered_services.append(format_expense_entry(full_name_source, net_price, gross_price))
                        last_item = None
                        continue

                    product_name = clean_product_name(full_name_source)
                    full_raw_desc = "\n".join(combined_raw_desc_lines).strip()
                    found_codes: List[str] = []
                    for c in WIPE_CODE_REGEX.findall(full_raw_desc):
                        if c not in found_codes:
                            found_codes.append(c)

                    total_net_all += net_price
                    total_vat_all += vat_amount
                    total_gross_all += gross_price

                    new_item = {
                        "line_index": len(parsed_items) + 1,
                        "product_name": product_name,
                        "raw_description": full_raw_desc,
                        "wipe_codes": found_codes,
                        "unit": unit,
                        "initial_quantity": quantity,
                        "current_quantity": quantity,
                        "unit_price_net": unit_price,
                        "vat_rate": vat_rate,
                        "total_net": net_price,
                        "total_vat": vat_amount,
                        "total_gross": gross_price,
                        "is_fully_deducted": False
                    }
                    parsed_items.append(new_item)
                    last_item = new_item
                else:
                    if not is_boilerplate_or_wipe_line(line_str):
                        pending_lines.append(line_str)
                    elif pending_lines:
                        pending_lines.append(line_str)
                    elif last_item is not None:
                        last_item["raw_description"] = (last_item["raw_description"] + "\n" + line_str).strip()
                        codes = WIPE_CODE_REGEX.findall(line_str)
                        for c in codes:
                            if c not in last_item["wipe_codes"]:
                                last_item["wipe_codes"].append(c)
                    else:
                        pending_lines.append(line_str)

    return {
        "supplier": {
            "name": supplier_name,
            "tax_number": supplier_tax,
            "eu_tax_number": supplier_eu_tax,
            "address": supplier_address,
            "bank_account": supplier_bank
        },
        "invoice": {
            "invoice_number": invoice_number,
            "issue_date": issue_date,
            "delivery_date": delivery_date,
            "due_date": due_date,
            "payment_method": payment_method,
            "total_net_amount": total_net_all,
            "total_vat_amount": total_vat_all,
            "total_gross_amount": total_gross_all,
            "status": "ACTIVE"
        },
        "items": parsed_items,
        "filtered_services": filtered_services
    }


# ==========================================
# Parser 2: Billingo
# ==========================================
def _parse_billingo(pdf, page1_text: str) -> Dict[str, Any]:
    page1 = pdf.pages[0]
    left_col_text = _extract_left_column_text(page1, height_ratio=0.48)

    supplier_name = "Ismeretlen Beszállító"
    supplier_tax = ""
    supplier_eu_tax = None
    supplier_bank = None
    supplier_address = ""

    tax_match = TAX_NUM_REGEX.search(left_col_text)
    if tax_match:
        supplier_tax = tax_match.group(1).strip()

    eu_match = BILLINGO_EU_TAX_REGEX.search(left_col_text)
    if eu_match:
        supplier_eu_tax = eu_match.group(1).strip()

    bank_match = BANK_ACC_REGEX.search(left_col_text)
    if bank_match:
        supplier_bank = bank_match.group(1).strip()

    left_lines = [l.strip() for l in left_col_text.splitlines() if l.strip()]
    in_elado = False
    addr_lines = []
    for line in left_lines:
        upper = line.upper()
        if upper.startswith("ELADÓ"):
            in_elado = True
            continue
        if not in_elado:
            continue
        if any(upper.startswith(stop) for stop in [
            "ADÓSZÁM:", "KÖZÖSSÉGI", "BANKSZÁMLASZÁM:", "BANK NEVE:", "SZÁMLA KELTE", "TELJESÍTÉS"
        ]):
            break
        if supplier_name == "Ismeretlen Beszállító":
            supplier_name = line
        else:
            addr_lines.append(line)

    if addr_lines:
        supplier_address = ", ".join(addr_lines)

    inv_match = BILLINGO_INVOICE_NUM_REGEX.search(page1_text)
    invoice_number = "UNKNOWN-INVOICE"
    if inv_match:
        invoice_number = re.sub(r'\s*/\s*', '/', inv_match.group(1).strip())

    issue_date = parse_date(BILLINGO_ISSUE_DATE_REGEX.search(page1_text).group(1)) if BILLINGO_ISSUE_DATE_REGEX.search(page1_text) else None
    delivery_date = parse_date(BILLINGO_DELIVERY_DATE_REGEX.search(page1_text).group(1)) if BILLINGO_DELIVERY_DATE_REGEX.search(page1_text) else None
    due_date = parse_date(BILLINGO_DUE_DATE_REGEX.search(page1_text).group(1)) if BILLINGO_DUE_DATE_REGEX.search(page1_text) else None
    pay_match = BILLINGO_PAYMENT_REGEX.search(page1_text)
    payment_method = pay_match.group(1).strip() if pay_match else None

    parsed_items: List[Dict[str, Any]] = []
    filtered_services: List[str] = []
    last_item: Optional[Dict[str, Any]] = None

    total_net_all = 0
    total_vat_all = 0
    total_gross_all = 0

    for page in pdf.pages:
        page_text = page.extract_text() or ""
        in_table = False
        for line in page_text.splitlines():
            line_str = line.strip()
            if not line_str:
                continue
            upper_line = line_str.upper()

            if "MEGNEVEZÉS" in upper_line and "MENNYISÉG" in upper_line:
                in_table = True
                continue

            if not in_table:
                continue

            if any(stop in upper_line for stop in [
                "NETTÓ ÖSSZEG:",
                "FIZETENDŐ BRUTTÓ VÉGÖSSZEG:",
                "EZ A SZÁMLA BILLINGO"
            ]) or re.match(r'^\d+/\d+\s+OLDAL', upper_line):
                in_table = False
                continue

            row_match = BILLINGO_ITEM_ROW_REGEX.match(line_str)
            if row_match:
                raw_name = row_match.group(2).strip()
                quantity = int(row_match.group(3))
                unit = row_match.group(4).strip()
                unit_price = parse_integer(row_match.group(5))
                net_price = parse_integer(row_match.group(6))
                vat_rate = re.sub(r'\s+', '', row_match.group(7).strip())
                gross_price = parse_integer(row_match.group(8))
                vat_amount = max(0, gross_price - net_price)

                if is_service_item(raw_name, unit):
                    filtered_services.append(format_expense_entry(raw_name, net_price, gross_price))
                    last_item = None
                    continue

                product_name = clean_product_name(raw_name)
                found_codes = WIPE_CODE_REGEX.findall(line_str)

                total_net_all += net_price
                total_vat_all += vat_amount
                total_gross_all += gross_price

                new_item = {
                    "line_index": len(parsed_items) + 1,
                    "product_name": product_name,
                    "raw_description": line_str,
                    "wipe_codes": found_codes,
                    "unit": unit,
                    "initial_quantity": quantity,
                    "current_quantity": quantity,
                    "unit_price_net": unit_price,
                    "vat_rate": vat_rate,
                    "total_net": net_price,
                    "total_vat": vat_amount,
                    "total_gross": gross_price,
                    "is_fully_deducted": False
                }
                parsed_items.append(new_item)
                last_item = new_item
            else:
                if last_item is not None:
                    last_item["raw_description"] = (last_item["raw_description"] + "\n" + line_str).strip()
                    for c in WIPE_CODE_REGEX.findall(line_str):
                        if c not in last_item["wipe_codes"]:
                            last_item["wipe_codes"].append(c)

    return {
        "supplier": {
            "name": supplier_name,
            "tax_number": supplier_tax,
            "eu_tax_number": supplier_eu_tax,
            "address": supplier_address,
            "bank_account": supplier_bank
        },
        "invoice": {
            "invoice_number": invoice_number,
            "issue_date": issue_date,
            "delivery_date": delivery_date,
            "due_date": due_date,
            "payment_method": payment_method,
            "total_net_amount": total_net_all,
            "total_vat_amount": total_vat_all,
            "total_gross_amount": total_gross_all,
            "status": "ACTIVE"
        },
        "items": parsed_items,
        "filtered_services": filtered_services
    }


# ==========================================
# Parser 3: JMStock / QrSzla (Extreme GSM)
# ==========================================
def _parse_jmstock(pdf, page1_text: str) -> Dict[str, Any]:
    page1 = pdf.pages[0]
    left_col_text = _extract_left_column_text(page1, height_ratio=0.22)

    supplier_name = "Ismeretlen Beszállító"
    supplier_tax = ""
    supplier_eu_tax = None
    supplier_bank = None
    supplier_address = ""

    tax_match = re.search(r'Adószám:\s*([0-9\-]+)', left_col_text, re.IGNORECASE)
    if tax_match:
        supplier_tax = tax_match.group(1).strip()

    eu_match = re.search(r'Köz\.?\s*adószám:\s*([A-Z]{2}\d+)', left_col_text, re.IGNORECASE)
    if eu_match:
        supplier_eu_tax = eu_match.group(1).strip()

    bank_match = re.search(r'Bankszámla(?:szám)?:\s*([0-9\-]+)', left_col_text, re.IGNORECASE)
    if bank_match:
        supplier_bank = bank_match.group(1).strip()

    left_lines = [l.strip() for l in left_col_text.splitlines() if l.strip()]
    in_kibocsato = False
    addr_lines = []
    for line in left_lines:
        lower = line.lower()
        if "számlakibocsátó" in lower:
            in_kibocsato = True
            continue
        if not in_kibocsato:
            continue
        if any(lower.startswith(stop) for stop in ["adószám", "köz.adószám", "bankszámla", "swift", "számlaszám"]):
            break
        if supplier_name == "Ismeretlen Beszállító":
            supplier_name = line
        else:
            addr_lines.append(line)

    if addr_lines:
        supplier_address = ", ".join(addr_lines)

    invoice_number = "UNKNOWN-INVOICE"
    payment_method = None
    delivery_date = None
    issue_date = None
    due_date = None

    p1_lines = [l.strip() for l in page1_text.splitlines() if l.strip()]
    for line in p1_lines:
        meta_match = JMSTOCK_META_ROW_REGEX.match(line)
        if meta_match:
            invoice_number = meta_match.group(1).strip()
            payment_method = meta_match.group(2).strip()
            delivery_date = parse_date(meta_match.group(3))
            issue_date = parse_date(meta_match.group(4))
            due_date = parse_date(meta_match.group(5))
            break

    parsed_items: List[Dict[str, Any]] = []
    filtered_services: List[str] = []
    last_item: Optional[Dict[str, Any]] = None
    last_was_filtered_service = False

    total_net_all = 0
    total_vat_all = 0
    total_gross_all = 0

    for page in pdf.pages:
        page_text = page.extract_text() or ""
        in_table = False

        for line in page_text.splitlines():
            line_str = line.strip()
            if not line_str:
                continue

            if "Megnevezés" in line_str and "Mértékegység" in line_str and "Mennyiség" in line_str:
                in_table = True
                continue

            if not in_table:
                continue

            if line_str == "Hivatkozás":
                continue

            if any(stop in line_str for stop in [
                "Az árút mennyiségben",
                "A csomagolási termékdíj",
                "27%-s ÁFA",
                "Áthárított ÁFA",
                "Összesen",
                "Kerekítés",
                "Fizetendő",
                "Készítette:"
            ]):
                in_table = False
                continue

            row_match = JMSTOCK_ITEM_ROW_REGEX.match(line_str)
            if row_match:
                short_name = row_match.group(2).strip()
                unit = row_match.group(3).strip()
                quantity = int(row_match.group(4))
                nums_part = row_match.group(5)

                unit_price, net_price, vat_amount, gross_price = split_four_numbers(nums_part, quantity)

                if is_service_item(short_name, unit):
                    filtered_services.append(format_expense_entry(short_name, net_price, gross_price))
                    last_item = None
                    last_was_filtered_service = True
                    continue

                last_was_filtered_service = False
                found_codes = WIPE_CODE_REGEX.findall(line_str)

                total_net_all += net_price
                total_vat_all += vat_amount
                total_gross_all += gross_price

                new_item = {
                    "line_index": len(parsed_items) + 1,
                    "product_name": short_name,
                    "raw_description": line_str,
                    "wipe_codes": found_codes,
                    "unit": unit,
                    "initial_quantity": quantity,
                    "current_quantity": quantity,
                    "unit_price_net": unit_price,
                    "vat_rate": "27%",
                    "total_net": net_price,
                    "total_vat": vat_amount,
                    "total_gross": gross_price,
                    "is_fully_deducted": False
                }
                parsed_items.append(new_item)
                last_item = new_item
            else:
                if last_was_filtered_service:
                    continue
                if last_item is not None:
                    ref_vat_match = JMSTOCK_REF_VAT_ROW_REGEX.match(line_str)
                    if ref_vat_match:
                        vat_tok = ref_vat_match.group(2).strip()
                        last_item["vat_rate"] = f"{vat_tok}%" if vat_tok.isdigit() else vat_tok
                        last_item["raw_description"] = (last_item["raw_description"] + "\n" + line_str).strip()
                    else:
                        last_item["raw_description"] = (last_item["raw_description"] + "\n" + line_str).strip()
                        if is_service_item(line_str):
                            # Reclassify item as service if its full 3rd-line description matches expense keywords
                            removed = parsed_items.pop()
                            total_net_all -= removed["total_net"]
                            total_vat_all -= removed["total_vat"]
                            total_gross_all -= removed["total_gross"]
                            filtered_services.append(
                                format_expense_entry(line_str, removed["total_net"], removed["total_gross"])
                            )
                            last_item = None
                            last_was_filtered_service = True
                            continue
                        if len(line_str) > len(last_item["product_name"]) and not WIPE_CODE_REGEX.search(line_str):
                            last_item["product_name"] = line_str
                    for c in WIPE_CODE_REGEX.findall(line_str):
                        if c not in last_item["wipe_codes"]:
                            last_item["wipe_codes"].append(c)

    return {
        "supplier": {
            "name": supplier_name,
            "tax_number": supplier_tax,
            "eu_tax_number": supplier_eu_tax,
            "address": supplier_address,
            "bank_account": supplier_bank
        },
        "invoice": {
            "invoice_number": invoice_number,
            "issue_date": issue_date,
            "delivery_date": delivery_date,
            "due_date": due_date,
            "payment_method": payment_method,
            "total_net_amount": total_net_all,
            "total_vat_amount": total_vat_all,
            "total_gross_amount": total_gross_all,
            "status": "ACTIVE"
        },
        "items": parsed_items,
        "filtered_services": filtered_services
    }


# ==========================================
# Parser 4: GLS GOLD (Logistics Expense Invoice)
# ==========================================
def _parse_gls_gold(pdf, page1_text: str) -> Dict[str, Any]:
    supplier_name = "GLS General Logistics Systems Hungary Kft."
    supplier_tax = "12369410-2-44"
    supplier_eu_tax = "HU12369410"
    supplier_address = "HU - Alsónémedi 2351, GLS Európa u. 2."
    supplier_bank = None

    bank_match = BANK_ACC_REGEX.search(page1_text)
    if bank_match:
        supplier_bank = bank_match.group(1).strip()

    invoice_number = "UNKNOWN-INVOICE"
    delivery_date = None
    issue_date = None
    due_date = None
    payment_method = None

    for line in page1_text.splitlines():
        m = GLS_META_ROW_REGEX.match(line.strip())
        if m:
            invoice_number = m.group(1).strip()
            delivery_date = parse_date(m.group(2))
            issue_date = parse_date(m.group(3))
            payment_method = m.group(4).strip()
            due_date = parse_date(m.group(5))
            break

    filtered_services: List[str] = []
    pending_desc: List[str] = []
    total_net_all = 0
    total_vat_all = 0
    total_gross_all = 0

    for page in pdf.pages:
        page_text = page.extract_text() or ""
        in_table = False
        for line in page_text.splitlines():
            line_str = line.strip()
            if not line_str:
                continue
            if "Description" in line_str and "Quantity" in line_str:
                in_table = True
                continue
            if line_str == "Q.u.":
                continue
            if not in_table:
                continue
            if any(stop in line_str for stop in ["Nettó/Net", "Fizetendő (HUF)", "A számla közvetített"]):
                in_table = False
                continue

            row_match = GLS_ITEM_ROW_REGEX.match(line_str)
            if row_match:
                inline_name = (row_match.group(1) or "").strip()
                qty = int(float(row_match.group(3).replace(",", ".")))
                pre_vat = row_match.group(4)
                post_vat = row_match.group(6)

                unit_price, net_price = split_two_numbers(pre_vat, lambda u, n: abs(u * qty - n))
                vat_amount, gross_price = split_two_numbers(post_vat, lambda v, g: abs((net_price + v) - g))

                name_parts = [p for p in pending_desc + ([inline_name] if inline_name else []) if p]
                pending_desc = []
                service_title = " ".join(name_parts).strip().rstrip("/").strip() or "GLS szállítási díj"
                filtered_services.append(format_expense_entry(service_title, net_price, gross_price))

                total_net_all += net_price
                total_vat_all += vat_amount
                total_gross_all += gross_price
            else:
                # Skip English translation continuation lines that wrap below the 'pcs' line in GLS Gold
                if line_str.lower() in {"domestic delivery", "transport fee (use of road)", "card service"}:
                    continue
                pending_desc.append(line_str)

    return {
        "supplier": {
            "name": supplier_name,
            "tax_number": supplier_tax,
            "eu_tax_number": supplier_eu_tax,
            "address": supplier_address,
            "bank_account": supplier_bank
        },
        "invoice": {
            "invoice_number": invoice_number,
            "issue_date": issue_date,
            "delivery_date": delivery_date,
            "due_date": due_date,
            "payment_method": payment_method,
            "total_net_amount": total_net_all,
            "total_vat_amount": total_vat_all,
            "total_gross_amount": total_gross_all,
            "status": "ACTIVE"
        },
        "items": [],
        "filtered_services": filtered_services
    }


# ==========================================
# Failsafe Validation
# ==========================================
def _validate_parsed_result(result: Dict[str, Any], format_type: str) -> None:
    """
    Validates that all mandatory invoice fields and line items were extracted properly.
    Raises UnsupportedInvoiceFormatError with a developer-actionable message if anything is missing or broken.
    """
    supplier = result.get("supplier") or {}
    invoice = result.get("invoice") or {}
    items = result.get("items") or []
    filtered_services = result.get("filtered_services") or []

    supplier_name = (supplier.get("name") or "").strip()
    invoice_number = (invoice.get("invoice_number") or "").strip()

    if not supplier_name or supplier_name == "Ismeretlen Beszállító":
        raise UnsupportedInvoiceFormatError(
            f"A számla ({format_type}) feldolgozása során nem sikerült kiolvasni a beszállító nevét. "
            "Kérjük, jelezd a fejlesztőnek, hogy adja hozzá vagy frissítse ezt a számlasablont a rendszerben!"
        )

    if not invoice_number or invoice_number == "UNKNOWN-INVOICE":
        raise UnsupportedInvoiceFormatError(
            f"A számla ({format_type}) feldolgozása során nem sikerült kiolvasni a számlaszámot. "
            "Kérjük, jelezd a fejlesztőnek, hogy adja hozzá vagy frissítse ezt a számlasablont a rendszerben!"
        )

    if len(items) == 0 and len(filtered_services) == 0:
        raise UnsupportedInvoiceFormatError(
            f"A számla ({format_type}, {invoice_number}) tételeit nem sikerült kiolvasni. "
            "Valószínűleg új vagy eltérő számlasablonról van szó. Kérjük, küldd el a PDF-et a fejlesztőnek, "
            "hogy hozzáadja a támogatott formátumokhoz!"
        )

    for idx, item in enumerate(items, start=1):
        prod_name = (item.get("product_name") or "").strip()
        if not prod_name or prod_name == "Ismeretlen termék":
            raise UnsupportedInvoiceFormatError(
                f"A számla ({format_type}, {invoice_number}) {idx}. tételének terméknevét nem sikerült biztonságosan "
                "kiolvasni. Kérjük, jelezd a fejlesztőnek, hogy egészítse ki a parsert ezzel a számlaformátummal!"
            )


# ==========================================
# Main Entry Point
# ==========================================
def parse_invoice_pdf(file_source: Union[str, bytes, io.BytesIO]) -> Dict[str, Any]:
    """
    Parses a supported invoice PDF (Számlázz.hu, Billingo, JMStock/QrSzla, GLS Gold) into structured JSON.
    Failsafe: raises UnsupportedInvoiceFormatError if the PDF template is unknown or cannot be reliably parsed.
    """
    with _open_pdf(file_source) as pdf:
        if not pdf.pages:
            raise UnsupportedInvoiceFormatError("A feltöltött PDF fájl üres (nem tartalmaz oldalakat).")

        page1_text = pdf.pages[0].extract_text() or ""
        full_text = "\n".join((p.extract_text() or "") for p in pdf.pages).strip()

        if not full_text:
            raise UnsupportedInvoiceFormatError(
                "A feltöltött PDF nem tartalmaz kiolvasható szövegréteget (valószínűleg szkennelt kép). "
                "Kérjük, jelezd a fejlesztőnek, ha ilyen számlát szeretnél feldolgoztatni!"
            )

        format_type = detect_invoice_format(full_text, page1_text)
        if format_type is None:
            raise UnsupportedInvoiceFormatError(
                "Ismeretlen vagy nem támogatott számlaformátum! A rendszer jelenleg ezt a PDF számlasablont "
                "nem ismeri (támogatott: Számlázz.hu, Billingo, JMStock/QrSzla, GLS). "
                "Kérjük, küldd el ezt a PDF számlát a fejlesztőnek, hogy hozzáadja a rendszerhez!"
            )

        if format_type == "SZAMLAZZ_HU":
            result = _parse_szamlazz_hu(pdf, page1_text)
        elif format_type == "BILLINGO":
            result = _parse_billingo(pdf, page1_text)
        elif format_type == "JMSTOCK":
            result = _parse_jmstock(pdf, page1_text)
        elif format_type == "GLS_GOLD":
            result = _parse_gls_gold(pdf, page1_text)
        else:
            raise UnsupportedInvoiceFormatError(
                f"Nem támogatott számlaformátum ({format_type}). Kérjük, jelezd a fejlesztőnek!"
            )

        _validate_parsed_result(result, format_type)
        return result
