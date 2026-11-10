import io
import re
import csv
from datetime import datetime, date
from typing import Dict, Any, List, Optional, Union
try:
    import openpyxl
except ImportError:
    openpyxl = None

WIPE_CODE_REGEX = re.compile(r'\b[A-Z0-9]{4}(?:-[A-Z0-9]{4}){3}\b')


def parse_numeric(val: Any) -> Optional[int]:
    """Cleans numeric values, removes spaces, currency symbols, and non-breaking spaces."""
    if val is None:
        return None
    if isinstance(val, (int, float)):
        return int(round(val))
    # Replace non-breaking spaces and regular spaces
    val_str = str(val).replace('\xa0', '').replace(' ', '').replace('Ft', '').replace('HUF', '').strip()
    # Match negative or positive integer
    cleaned = re.sub(r'[^\d\-]', '', val_str)
    try:
        return int(cleaned) if cleaned else None
    except ValueError:
        return None


def parse_date_value(val: Any) -> Optional[str]:
    """Normalizes dates (datetime, date, or strings) to YYYY-MM-DD."""
    if val is None:
        return None
    if isinstance(val, (datetime, date)):
        return val.strftime("%Y-%m-%d")
    val_str = str(val).strip()
    for fmt in ["%Y-%m-%d", "%Y.%m.%d", "%Y/%m/%d", "%d.%m.%Y", "%d/%m/%Y"]:
        try:
            return datetime.strptime(val_str.rstrip("."), fmt).strftime("%Y-%m-%d")
        except ValueError:
            continue
    return val_str if val_str else None


def is_sales_header(header_names: List[str]) -> bool:
    """Detects if sheet columns correspond to a sales order sheet."""
    joined = " ".join([h.lower() for h in header_names])
    has_product = any(term in joined for term in ["termék", "megnevezés", "cikk"])
    has_sales_indicators = any(term in joined for term in ["eladási", "eladás", "profit", "vevő", "dátum"])
    return has_product and has_sales_indicators


def parse_excel_sheet(file_source: Union[str, bytes, io.BytesIO], filename: str = "") -> Dict[str, Any]:
    """
    Parses an Excel (.xlsx) or CSV file containing webshop/retail sales orders or stock items.
    Returns structured data with order grouping, customer info, sales prices, cost prices, and profit metrics.
    """
    # Detect CSV
    if filename.lower().endswith(".csv") or (isinstance(file_source, bytes) and file_source[:10].startswith(b"#") and b"\t" in file_source[:50]):
        return parse_csv_stream(file_source)

    excel_stream = io.BytesIO(file_source) if isinstance(file_source, bytes) else file_source
    wb = openpyxl.load_workbook(excel_stream, data_only=True)

    result_sheets = []
    all_extracted_items = []
    grouped_orders_dict: Dict[str, Dict[str, Any]] = {}

    for sheet_name in wb.sheetnames:
        sheet = wb[sheet_name]
        rows = list(sheet.iter_rows(values_only=True))
        if not rows:
            continue

        header_idx = -1
        headers = []

        # Find header row
        for idx, row in enumerate(rows):
            non_empty = [str(cell).strip() for cell in row if cell is not None and str(cell).strip()]
            row_str = " ".join(non_empty).lower()
            if any(term in row_str for term in ["termék", "megnevezés", "dátum", "eladási", "ár", "név"]):
                header_idx = idx
                headers = []
                for i, c in enumerate(row):
                    if c is not None and str(c).strip():
                        headers.append(str(c).strip())
                    else:
                        # If first column has no header, check if next rows have order numbers like #1234
                        headers.append("Rendelésszám" if i == 0 else f"col_{i}")
                break

        if header_idx == -1:
            continue

        sheet_is_sales = is_sales_header(headers)

        for row in rows[header_idx + 1:]:
            if not any(row):
                continue

            row_dict = {}
            for i, val in enumerate(row):
                if i < len(headers):
                    row_dict[headers[i]] = val

            # Parse fields according to matched headers
            order_number = None
            order_date = None
            customer_name = None
            product_name = None
            selling_price = None
            cost_price = None
            profit_gross = None
            profit_net = None
            wipe_codes = []

            for col_name, val in row_dict.items():
                if val is None or str(val).strip() == "":
                    continue
                c_low = col_name.lower()
                val_str = str(val).strip()

                # Extract wipe codes
                for code in WIPE_CODE_REGEX.findall(val_str):
                    if code not in wipe_codes:
                        wipe_codes.append(code)

                # Order number detection (#67353 or similar)
                if any(k in c_low for k in ["rendelés", "sorszám", "azonosító", "#", "order"]) or (c_low.startswith("col_") and val_str.startswith("#")):
                    order_number = val_str
                # Date
                elif any(k in c_low for k in ["dátum", "date"]):
                    order_date = parse_date_value(val)
                # Customer name
                elif any(k in c_low for k in ["név", "vevő", "vásárló", "customer"]):
                    customer_name = val_str
                # Product name
                elif any(k in c_low for k in ["termék", "megnevezés", "cikk", "product"]):
                    product_name = val_str
                # Selling price (Eladási ár)
                elif any(k in c_low for k in ["eladási", "eladás", "sale price", "értékesítés"]):
                    selling_price = parse_numeric(val)
                # Cost price (Bekerülési ár)
                elif any(k in c_low for k in ["beker", "beszerzési", "bekerülés", "cost"]):
                    cost_price = parse_numeric(val)
                # Gross Profit
                elif "profit br" in c_low or "bruttó profit" in c_low:
                    profit_gross = parse_numeric(val)
                # Net Profit
                elif "netto profit" in c_low or "nettó profit" in c_low or "net profit" in c_low:
                    profit_net = parse_numeric(val)
                # Fallback generic price
                elif "ár" in c_low and selling_price is None:
                    selling_price = parse_numeric(val)

            if product_name:
                item_record = {
                    "line_index": len(all_extracted_items) + 1,
                    "order_number": order_number,
                    "order_date": order_date,
                    "customer_name": customer_name,
                    "product_name": product_name,
                    "quantity": 1,
                    "selling_price_gross": selling_price,
                    "cost_price_gross": cost_price,
                    "profit_gross": profit_gross,
                    "profit_net": profit_net,
                    "wipe_codes": wipe_codes,
                    "raw_data": {str(k): str(v) for k, v in row_dict.items() if v is not None}
                }
                all_extracted_items.append(item_record)

                # Group by order number if present
                order_key = order_number or f"UNGROUPED_{len(grouped_orders_dict)}"
                if order_key not in grouped_orders_dict:
                    grouped_orders_dict[order_key] = {
                        "order_number": order_number,
                        "order_date": order_date,
                        "customer_name": customer_name,
                        "items": []
                    }
                grouped_orders_dict[order_key]["items"].append(item_record)

        result_sheets.append({
            "sheet_name": sheet_name,
            "row_count": len(rows),
            "is_sales_sheet": sheet_is_sales,
            "extracted_items_count": len(all_extracted_items)
        })

    return {
        "file_type": "EXCEL",
        "sheet_type": "SALES_ORDERS" if any(s["is_sales_sheet"] for s in result_sheets) else "STOCK_ITEMS",
        "sheets": result_sheets,
        "orders": list(grouped_orders_dict.values()),
        "items": all_extracted_items,
        "total_items": len(all_extracted_items),
        "total_orders": len(grouped_orders_dict)
    }


def parse_csv_stream(file_source: Union[str, bytes, io.BytesIO]) -> Dict[str, Any]:
    """Helper to parse tab-separated or comma-separated sales order text/CSV."""
    if isinstance(file_source, bytes):
        text_content = file_source.decode("utf-8", errors="replace")
    elif isinstance(file_source, io.BytesIO):
        text_content = file_source.getvalue().decode("utf-8", errors="replace")
    elif isinstance(file_source, str) and ("\n" in file_source or "\t" in file_source or "," in file_source):
        text_content = file_source
    else:
        with open(file_source, "r", encoding="utf-8", errors="replace") as f:
            text_content = f.read()

    lines = [l for l in text_content.splitlines() if l.strip()]
    if not lines:
        return {"file_type": "CSV", "orders": [], "items": [], "total_items": 0}

    # Detect delimiter: tab or comma or semicolon
    first_line = lines[0]
    delimiter = "\t" if "\t" in first_line else (";" if ";" in first_line else ",")

    reader = csv.reader(lines, delimiter=delimiter)
    all_rows = list(reader)
    if not all_rows:
        return {"file_type": "CSV", "orders": [], "items": [], "total_items": 0}

    # Header detection
    headers = [h.strip() for h in all_rows[0]]
    if len(headers) > 0 and headers[0] == "":
        headers[0] = "Rendelésszám"

    items = []
    grouped_orders = {}

    for row in all_rows[1:]:
        if not any(row):
            continue
        row_dict = {headers[i]: row[i].strip() if i < len(row) else "" for i in range(len(headers))}

        order_num = row_dict.get("Rendelésszám") or (row[0].strip() if len(row) > 0 and row[0].strip().startswith("#") else None)
        order_date = parse_date_value(row_dict.get("Dátum"))
        customer = row_dict.get("Név") or row_dict.get("Vevő")
        product = row_dict.get("Termék") or row_dict.get("Megnevezés")

        sell_p = parse_numeric(row_dict.get("Eladási Ár br") or row_dict.get("Eladási ár"))
        cost_p = parse_numeric(row_dict.get("Beker Ár br") or row_dict.get("Bekerülési ár"))
        prof_br = parse_numeric(row_dict.get("Profit Br"))
        prof_net = parse_numeric(row_dict.get("Netto Profit") or row_dict.get("Nettó profit"))

        if product:
            item_obj = {
                "line_index": len(items) + 1,
                "order_number": order_num,
                "order_date": order_date,
                "customer_name": customer,
                "product_name": product,
                "quantity": 1,
                "selling_price_gross": sell_p,
                "cost_price_gross": cost_p,
                "profit_gross": prof_br,
                "profit_net": prof_net,
                "wipe_codes": WIPE_CODE_REGEX.findall(" ".join(row))
            }
            items.append(item_obj)

            order_key = order_num or f"UNGROUPED_{len(grouped_orders)}"
            if order_key not in grouped_orders:
                grouped_orders[order_key] = {
                    "order_number": order_num,
                    "order_date": order_date,
                    "customer_name": customer,
                    "items": []
                }
            grouped_orders[order_key]["items"].append(item_obj)

    return {
        "file_type": "CSV",
        "sheet_type": "SALES_ORDERS",
        "orders": list(grouped_orders.values()),
        "items": items,
        "total_items": len(items),
        "total_orders": len(grouped_orders)
    }
