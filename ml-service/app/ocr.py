from dataclasses import dataclass
import re
import easyocr
import numpy as np
import cv2

EMAIL_RE = re.compile(r"[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")
PHONE_RE = re.compile(r"(?:\+|00)?\d[\d\s().-]{6,}\d")
WEBSITE_RE = re.compile(r"\b(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?\b")


@dataclass
class OcrLine:
    text: str
    bbox: tuple[int, int, int, int]
    confidence: float = 0.0

_reader = None


def _get_reader():
    global _reader
    if _reader is None:
        # gpu=False keeps this on the CPU-only torch build in requirements.txt
        _reader = easyocr.Reader(["en"], gpu=False)
    return _reader


@dataclass
class OcrResult:
    text: str
    boxes: list  # (x1, y1, x2, y2) text bounding boxes, used for logo cropping
    lines: list[OcrLine]
    avg_confidence: float = 0.0


@dataclass
class OcrAttempt:
    variant: str
    result: OcrResult
    score: float


def _score_result(result: OcrResult) -> float:
    text = result.text or ""
    letters = sum(1 for ch in text if ch.isalpha())
    digits = sum(1 for ch in text if ch.isdigit())
    punctuation = sum(1 for ch in text if not ch.isalnum() and not ch.isspace())
    lines = [line for line in text.splitlines() if line.strip()]
    normalized = re.sub(r"\s*@\s*", "@", text)
    normalized = re.sub(r"\s*\.\s*", ".", normalized)
    contact_hits = len(EMAIL_RE.findall(normalized)) + len(PHONE_RE.findall(normalized)) + len(WEBSITE_RE.findall(normalized))

    # Prefer results with actual letters and multiple text lines; confidence is
    # helpful, but OCR on business cards often needs a lexical signal too.
    return (
        result.avg_confidence * 120.0
        + letters * 3.0
        + len(lines) * 6.0
        + contact_hits * 20.0
        - digits * 1.5
        - punctuation * 0.5
    )


def _ocr_from_image(image: np.ndarray) -> OcrResult:
    reader = _get_reader()
    results = reader.readtext(image)

    lines = []
    boxes = []
    confidences = []
    structured_lines: list[OcrLine] = []

    for bbox, text, confidence in results:
        lines.append(text)
        confidences.append(confidence)
        xs = [point[0] for point in bbox]
        ys = [point[1] for point in bbox]
        rect = (int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys)))
        boxes.append(rect)
        structured_lines.append(OcrLine(text=text, bbox=rect, confidence=confidence))

    avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0
    return OcrResult(text="\n".join(lines), boxes=boxes, lines=structured_lines, avg_confidence=avg_confidence)


def run_best_ocr(images: dict[str, np.ndarray]) -> tuple[OcrResult, str, list[OcrAttempt]]:
    attempts: list[OcrAttempt] = []

    for variant, image in images.items():
        result = _ocr_from_image(image)
        attempts.append(OcrAttempt(variant=variant, result=result, score=_score_result(result)))

    best = max(attempts, key=lambda attempt: attempt.score)
    return best.result, best.variant, attempts
