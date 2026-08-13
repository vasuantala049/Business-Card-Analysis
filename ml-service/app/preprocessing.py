import cv2
import numpy as np


def preprocess_image(image: np.ndarray) -> np.ndarray:
    """Lightly denoise, boost contrast, upscale, and sharpen small text so OCR
    has a better chance on business-card crops and phone-camera shots.

    This intentionally avoids heavy thresholding because it can erase thin text
    strokes on clean cards or vector-like screenshots."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    denoised = cv2.fastNlMeansDenoising(gray, h=6)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    contrasted = clahe.apply(denoised)

    upscaled = cv2.resize(
        contrasted,
        None,
        fx=1.8,
        fy=1.8,
        interpolation=cv2.INTER_CUBIC,
    )

    sharpen_kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
    sharpened = cv2.filter2D(upscaled, -1, sharpen_kernel)

    deskewed = _deskew(sharpened)

    return cv2.cvtColor(deskewed, cv2.COLOR_GRAY2BGR)


def _deskew(gray: np.ndarray) -> np.ndarray:
    thresh = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV | cv2.THRESH_OTSU)[1]
    coords = np.column_stack(np.where(thresh > 0))
    if coords.size == 0:
        return gray

    angle = cv2.minAreaRect(coords)[-1]
    if angle < -45:
        angle = -(90 + angle)
    else:
        angle = -angle

    if abs(angle) < 0.5:
        return gray

    (h, w) = gray.shape
    center = (w // 2, h // 2)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
    return cv2.warpAffine(
        gray, matrix, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REPLICATE
    )
