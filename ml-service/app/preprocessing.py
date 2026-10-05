import cv2
import numpy as np


def _to_gray(image: np.ndarray) -> np.ndarray:
    if len(image.shape) == 2:
        return image
    return cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)


def _to_bgr(image: np.ndarray) -> np.ndarray:
    if len(image.shape) == 3:
        return image
    return cv2.cvtColor(image, cv2.COLOR_GRAY2BGR)


def _base_contrast(gray: np.ndarray) -> np.ndarray:
    denoised = cv2.fastNlMeansDenoising(gray, h=6)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    return clahe.apply(denoised)


def _enhance_for_ocr(gray: np.ndarray) -> np.ndarray:
    upscaled = cv2.resize(
        gray,
        None,
        fx=1.8,
        fy=1.8,
        interpolation=cv2.INTER_CUBIC,
    )

    sharpen_kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
    sharpened = cv2.filter2D(upscaled, -1, sharpen_kernel)

    return _deskew(sharpened)


def preprocess_image(image: np.ndarray) -> np.ndarray:
    """Lightly denoise, boost contrast, upscale, and sharpen small text so OCR
    has a better chance on business-card crops and phone-camera shots.

    This intentionally avoids heavy thresholding because it can erase thin text
    strokes on clean cards or vector-like screenshots."""
    gray = _to_gray(image)
    contrasted = _base_contrast(gray)
    enhanced = _enhance_for_ocr(contrasted)

    return _to_bgr(enhanced)


def build_ocr_variants(image: np.ndarray) -> dict[str, np.ndarray]:
    """Create a small set of OCR-ready image variants.

    The idea is to give OCR a few gentle and a few aggressive versions of the
    same card so we can pick the one that preserves contact details best.
    """
    gray = _to_gray(image)
    contrasted = _base_contrast(gray)
    enhanced = _enhance_for_ocr(contrasted)

    threshold = cv2.adaptiveThreshold(
        enhanced,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        11,
    )
    otsu = cv2.threshold(enhanced, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)[1]
    inverted = cv2.bitwise_not(otsu)

    h = gray.shape[0]
    contact_start = max(int(h * 0.45), 0)
    contact_slice = slice(contact_start, h)
    contact_raw = image[contact_slice, :]
    contact_gray = gray[contact_slice, :]
    contact_contrasted = _base_contrast(contact_gray)
    contact_enhanced = _enhance_for_ocr(contact_contrasted)
    contact_threshold = cv2.adaptiveThreshold(
        contact_enhanced,
        255,
        cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
        cv2.THRESH_BINARY,
        31,
        11,
    )

    return {
        "raw": _to_bgr(image),
        "enhanced": _to_bgr(enhanced),
        "threshold": _to_bgr(threshold),
        "otsu": _to_bgr(otsu),
        "inverted": _to_bgr(inverted),
        "contact_raw": _to_bgr(contact_raw),
        "contact_enhanced": _to_bgr(contact_enhanced),
        "contact_threshold": _to_bgr(contact_threshold),
    }


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
