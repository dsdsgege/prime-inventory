import io
import re
from datetime import datetime, date
from typing import Dict, Any, List, Optional, Union
import pdfplumber

# Regular expression for NMHH 4x4 data wipe codes (e.g. USWA-CFXS-ZGWF-YFD5)
WIPE_CODE_REGEX = re.compile(r'\b[A-Z0-9]{4}(?:-[A-Z0-9]{4}){3}\b')

# Blacklist of non-physical service keywords (lowercase)
SERVICE_BLACKLIST = [
    "csomagküldés",
    "csomagkuldes",
    "gls",
    "szállítás",
    "szallitas",
    "szállítási díj",
    "szallitasi dij",
    "fuvardíj",
    "fuvardij",
    "postaköltség",
    "postakoltseg",
    "utánvét",
    "utanvet",
    "futár",
    "futar",
    "kiszállítás"
]

# Patterns for invoice header metadata (supporting both spaced and unspaced text)
INVOICE_NUM_REGEX = re.compile(r'Sorszám:\s*([A-Z0-9\-]+)', re.IGNORECASE)
PAYMENT_METHOD_REGEX = re.compile(r'Fizetési\s*mód:\s*([^\n\r]+?)(?=\s+Teljesítés|\s+Kiállítás|\s+Adószám|\n|$)', re.IGNORECASE)
DELIVERY_DATE_REGEX = re.compile(r'Teljesítés\s*dátuma:\s*(\d{4}\.\d{2}\.\d{2}\.?)', re.IGNORECASE)
ISSUE_DATE_REGEX = re.compile(r'Kiállítás\s*dátuma:\s*(\d{4}\.\d{2}\.\d{2}\.?)', re.IGNORECASE)
DUE_DATE_REGEX = re.compile(r'Fizetési\s*határid[őöön]:\s*(\d{4}\.\d{2}\.\d{2}\.?)', re.IGNORECASE)

TAX_NUM_REGEX = re.compile(r'Adószám:\s*(\d{8}-\d-\d{2})(?:,\s*([A-Z]{2}\d+))?', re.IGNORECASE)
BANK_ACC_REGEX = re.compile(r'Bankszámlaszám:\s*([0-9\-]+)', re.IGNORECASE)

# Regex matching a line item row in Számlázz.hu text:
# e.g.: "SamsungGalaxyS25 2db 205000 410000 K.AFA 0 410000"
# or "Samsung Galaxy S25 2 db 205 000 410 000 K.AFA 0 410 000"
ITEM_ROW_REGEX = re.compile(
    r"^(.+?)\s+(\d+)\s*(db|DB|db\.)\s+(\d[\d\s]*?)\s+(\d[\d\s]*?)\s+(K\.AFA|\d+\%|[A-Za-z0-9\.\%]+)\s+(\d[\d\s]*?)\s+(\d[\d\s]*?)$"
)


def parse_date(date_str: Optional[str]) -> Optional[str]:
    """Parses date string (YYYY.MM.DD) and returns ISO formatted string YYYY-MM-DD."""
    if not date_str:
        return None
    cleaned = date_str.strip().rstrip(".")
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


def clean_product_name(raw_name: str) -> str:
    """Removes boilerplate legal phrases and wipe codes, restoring clean product title."""
    if not raw_name:
        return ""
    lines = [l.strip() for l in raw_name.splitlines() if l.strip()]
    product_lines = []
    for line in lines:
        lower = line.lower()
        if "különbözet" in lower or "kulonbozet" in lower or "használt" in lower or "hasznalt" in lower:
            break
        if "adat" in lower and ("törl" in lower or "torl" in lower):
            break
        if WIPE_CODE_REGEX.search(line):
            continue
        product_lines.append(line)

    clean_str = " ".join(product_lines).strip() if product_lines else raw_name
    # Strip any remaining boilerplate
    clean_str = re.sub(r'Különbözet[^\n\r]*', '', clean_str, flags=re.IGNORECASE)
    clean_str = re.sub(r'-használt[^\n\r]*', '', clean_str, flags=re.IGNORECASE)
    clean_str = re.sub(r'adat\s*t[öőön]rl[öőön]:?', '', clean_str, flags=re.IGNORECASE)
    clean_str = WIPE_CODE_REGEX.sub('', clean_str)
    return beautify_name(clean_str)


def is_service_item(text: str) -> bool:
    lower_text = text.lower()
    return any(keyword in lower_text for keyword in SERVICE_BLACKLIST)


def parse_invoice_pdf(file_source: Union[str, bytes, io.BytesIO]) -> Dict[str, Any]:
    """
    Parses a Számlázz.hu invoice PDF from a file path, bytes, or BytesIO object.
    Returns structured JSON with supplier, invoice header, line items, and NMHH wipe codes.
    """
    pdf_obj = io.BytesIO(file_source) if isinstance(file_source, bytes) else file_source

    with pdfplumber.open(pdf_obj) as pdf:
        if not pdf.pages:
            raise ValueError("The provided PDF file contains no pages.")

        page1 = pdf.pages[0]
        page1_text = page1.extract_text() or ""

        # ==========================================
        # 1. Supplier & Buyer Separation
        # ==========================================
        vevo_split = re.split(r'\bVEV[ŐÖőön]:', page1_text, flags=re.IGNORECASE)
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
            m = re.search(r'^(.*?(?:Kft\.|Bt\.|Zrt\.|Nyrt\.|Egyéni vállalkozó|KFT))', line, flags=re.IGNORECASE)
            if m:
                supplier_name = beautify_name(m.group(1).strip())
                break

        if supplier_name == "Ismeretlen Beszállító" and supplier_lines:
            first_line = re.sub(r'\bSZÁMLA\b.*$', '', supplier_lines[0]).strip()
            if first_line:
                supplier_name = beautify_name(first_line)

        # Gather supplier address
        addr_parts = []
        for line in supplier_lines:
            if line.startswith(supplier_name) or "SZÁMLA" in line or "Adószám:" in line or "Bank" in line or "Sorszám:" in line:
                continue
            clean_line = re.sub(r'(?:Sorszám:|Fizetési|Teljesítés|Kiállítás).*$', '', line).strip()
            if clean_line:
                addr_parts.append(beautify_name(clean_line))

        if addr_parts:
            supplier_address = ", ".join(addr_parts)

        # ==========================================
        # 2. Invoice Header Metadata
        # ==========================================
        invoice_num_match = INVOICE_NUM_REGEX.search(page1_text)
        invoice_number = invoice_num_match.group(1).strip() if invoice_num_match else "UNKNOWN-INVOICE"

        pay_method_match = PAYMENT_METHOD_REGEX.search(page1_text)
        payment_method = pay_method_match.group(1).strip() if pay_method_match else None

        delivery_date = parse_date(DELIVERY_DATE_REGEX.search(page1_text).group(1)) if DELIVERY_DATE_REGEX.search(page1_text) else None
        issue_date = parse_date(ISSUE_DATE_REGEX.search(page1_text).group(1)) if ISSUE_DATE_REGEX.search(page1_text) else None
        due_date = parse_date(DUE_DATE_REGEX.search(page1_text).group(1)) if DUE_DATE_REGEX.search(page1_text) else None

        # ==========================================
        # 3. Line Items Extraction (Multi-Page Text Parser)
        # ==========================================
        parsed_items: List[Dict[str, Any]] = []
        filtered_services: List[str] = []
        last_item: Optional[Dict[str, Any]] = None

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

                # Header detection: "Megnevezés Menny..."
                if "Megnevezés" in line_str and ("Menny" in line_str or "Egységár" in line_str):
                    in_table_section = True
                    continue

                if in_table_section:
                    # Check for section boundaries / footers
                    if any(stop_phrase in line_str for stop_phrase in [
                        "(folytatás",
                        "Összesen:",
                        "Levelezés:",
                        "Abizonylatot",
                        "A bizonylatot"
                    ]):
                        in_table_section = False
                        continue

                    # Try to match item row: Name + Qty + db + prices
                    row_match = ITEM_ROW_REGEX.match(line_str)
                    if row_match:
                        raw_name = row_match.group(1).strip()
                        quantity = int(row_match.group(2))
                        unit = row_match.group(3)
                        unit_price = parse_integer(row_match.group(4))
                        net_price = parse_integer(row_match.group(5))
                        vat_rate = row_match.group(6).strip()
                        vat_amount = parse_integer(row_match.group(7))
                        gross_price = parse_integer(row_match.group(8))

                        # Check if this row is a blacklisted service (e.g. GLS csomagküldés)
                        if is_service_item(raw_name):
                            filtered_services.append(clean_product_name(raw_name) or raw_name)
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
                        # Continuation line (e.g. boilerplate description or wipe codes)
                        if last_item is not None:
                            last_item["raw_description"] = (last_item["raw_description"] + "\n" + line_str).strip()
                            codes = WIPE_CODE_REGEX.findall(line_str)
                            for c in codes:
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
