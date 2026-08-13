from dataclasses import dataclass
import easyocr
import numpy as np
import cv2

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

    # Prefer results with actual letters and multiple text lines; confidence is
    # helpful, but OCR on business cards often needs a lexical signal too.
    return (
        result.avg_confidence * 120.0
        + letters * 3.0
        + len(lines) * 6.0
        - digits * 1.5
        - punctuation * 0.5
    )


def _ocr_from_image(image: np.ndarray) -> OcrResult:
    reader = _get_reader()
    results = reader.readtext(image)

    lines = []
    boxes = []
    confidences = []

    for bbox, text, confidence in results:
        lines.append(text)
        confidences.append(confidence)
        xs = [point[0] for point in bbox]
        ys = [point[1] for point in bbox]
        boxes.append((int(min(xs)), int(min(ys)), int(max(xs)), int(max(ys))))

    avg_confidence = sum(confidences) / len(confidences) if confidences else 0.0
    return OcrResult(text="\n".join(lines), boxes=boxes, avg_confidence=avg_confidence)


def run_best_ocr(images: dict[str, np.ndarray]) -> tuple[OcrResult, str, list[OcrAttempt]]:
    attempts: list[OcrAttempt] = []

    for variant, image in images.items():
        result = _ocr_from_image(image)
        attempts.append(OcrAttempt(variant=variant, result=result, score=_score_result(result)))

    best = max(attempts, key=lambda attempt: attempt.score)
    return best.result, best.variant, attempts
