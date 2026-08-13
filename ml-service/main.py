from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import logging
import numpy as np
import cv2

from app.preprocessing import preprocess_image
from app.ocr import run_best_ocr
from app.extraction import extract_fields
from app.logo_detection import extract_logo
from app.schemas import ExtractionResponse

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logger = logging.getLogger(__name__)

app = FastAPI(title="Business Card ML Service")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/extract", response_model=ExtractionResponse)
async def extract(file: UploadFile = File(...)):
    if not file.content_type or not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="Upload an image file")

    raw_bytes = await file.read()
    np_array = np.frombuffer(raw_bytes, dtype=np.uint8)
    image = cv2.imdecode(np_array, cv2.IMREAD_COLOR)
    if image is None:
        raise HTTPException(status_code=400, detail="Could not decode image")

    processed = preprocess_image(image)
    ocr_result, variant, attempts = run_best_ocr({"raw": image, "enhanced": processed})
    logger.info(
        "OCR variant scores: %s",
        ", ".join(
            f"{attempt.variant}={attempt.score:.1f}" for attempt in attempts
        ),
    )
    logger.info(
        "Chosen OCR variant=%s lines=%d text=%s",
        variant,
        len(ocr_result.text.splitlines()),
        ocr_result.text.replace("\n", " | "),
    )
    fields, source = extract_fields(ocr_result.text)
    logo_base64 = extract_logo(image if variant == "raw" else processed, ocr_result.boxes)

    return ExtractionResponse(
        raw_text=ocr_result.text,
        fields=fields,
        logo_image=logo_base64,
        extraction_source=source,
        confidence=ocr_result.avg_confidence,
    )
