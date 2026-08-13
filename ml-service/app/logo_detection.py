import base64
from typing import Optional
import cv2
import numpy as np


def extract_logo(image: np.ndarray, text_boxes: list) -> Optional[str]:
    """Heuristic logo crop: mask out every region OCR identified as text,
    then treat the largest remaining solid blob as the logo.
    Cheap to run and good enough for a card layout — swap in a trained
    detector later if you want higher precision."""
    h, w = image.shape[:2]
    mask = np.full((h, w), 255, dtype=np.uint8)

    padding = 6
    for (x1, y1, x2, y2) in text_boxes:
        cv2.rectangle(
            mask,
            (max(0, x1 - padding), max(0, y1 - padding)),
            (min(w, x2 + padding), min(h, y2 + padding)),
            0,
            -1,
        )

    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours:
        return None

    candidate = max(contours, key=cv2.contourArea)
    area = cv2.contourArea(candidate)

    # Ignore tiny specks and near-full-image blobs (usually background, not a logo)
    if area < (h * w * 0.005) or area > (h * w * 0.5):
        return None

    x, y, cw, ch = cv2.boundingRect(candidate)
    crop = image[y:y + ch, x:x + cw]

    success, buffer = cv2.imencode(".png", crop)
    if not success:
        return None

    return base64.b64encode(buffer).decode("utf-8")
