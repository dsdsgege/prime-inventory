import logging
from fastapi import FastAPI, File, UploadFile, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Dict, Any, List

from pdf_parser import parse_invoice_pdf
from excel_parser import parse_excel_sheet

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("parser-service")

app = FastAPI(
    title="Prime Mobile Parser Service",
    description="Microservice for extracting invoices (PDF) and spreadsheets (Excel) into structured inventory data.",
    version="1.0.0"
)

# Enable CORS for local or internal debugging
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health", tags=["Health"])
async def health_check() -> Dict[str, str]:
    return {
        "status": "UP",
        "service": "parser-service",
        "version": "1.0.0"
    }


@app.post("/parse/pdf", tags=["Parser"])
async def parse_pdf(file: UploadFile = File(...)) -> Dict[str, Any]:
    """
    Accepts an uploaded invoice PDF, extracts metadata, line items, and NMHH wipe codes.
    """
    if not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Filename is missing."
        )

    if not file.filename.lower().endswith(".pdf"):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid file extension for PDF parser: {file.filename}. Only .pdf is supported."
        )

    try:
        logger.info("Parsing PDF invoice: %s (%s)", file.filename, file.content_type)
        contents = await file.read()
        if len(contents) == 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file is empty.")

        parsed_data = parse_invoice_pdf(contents)
        logger.info(
            "Successfully parsed invoice %s: %d items extracted",
            parsed_data["invoice"]["invoice_number"],
            len(parsed_data["items"])
        )
        return parsed_data

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Failed to parse PDF file '%s': %s", file.filename, exc)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Could not parse invoice PDF: {str(exc)}"
        )


@app.post("/parse/excel", tags=["Parser"])
async def parse_excel(file: UploadFile = File(...)) -> Dict[str, Any]:
    """
    Accepts an uploaded Excel (.xlsx, .xlsm, .xls) or CSV/TSV sales order spreadsheet.
    """
    if not file.filename:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Filename is missing."
        )

    allowed_exts = (".xlsx", ".xlsm", ".xls", ".csv", ".tsv")
    if not file.filename.lower().endswith(allowed_exts):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Invalid file extension: {file.filename}. Supported: {', '.join(allowed_exts)}"
        )

    try:
        logger.info("Parsing spreadsheet file: %s", file.filename)
        contents = await file.read()
        if len(contents) == 0:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Uploaded file is empty.")

        parsed_data = parse_excel_sheet(contents, filename=file.filename)
        logger.info(
            "Successfully parsed spreadsheet %s: %d items, %d orders extracted (type: %s)",
            file.filename,
            parsed_data["total_items"],
            parsed_data.get("total_orders", 0),
            parsed_data.get("sheet_type", "UNKNOWN")
        )
        return parsed_data

    except HTTPException:
        raise
    except Exception as exc:
        logger.exception("Failed to parse spreadsheet file '%s': %s", file.filename, exc)
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"Could not parse spreadsheet: {str(exc)}"
        )
