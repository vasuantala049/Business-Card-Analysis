# ---------- Stage 1: builder ----------
FROM python:3.11-slim AS builder

ENV PIP_DEFAULT_TIMEOUT=600 \
    PIP_RETRIES=10 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    EASYOCR_MODULE_PATH=/opt/easyocr

# needed to import cv2 when downloading the models below
RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

WORKDIR /build

# CPU-only torch first (avoids multi-GB CUDA libs)
RUN pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install -r requirements.txt \
    && python -m spacy download en_core_web_sm

# Bake EasyOCR models into the image (add only the languages you use)
RUN python -c "import easyocr; easyocr.Reader(['en'], gpu=False, verbose=False)"


# ---------- Stage 2: runtime ----------
FROM python:3.11-slim

ENV PATH="/opt/venv/bin:$PATH" \
    EASYOCR_MODULE_PATH=/opt/easyocr \
    PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN apt-get update && apt-get install -y --no-install-recommends \
    libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=builder /opt/venv /opt/venv
COPY --from=builder /opt/easyocr /opt/easyocr
COPY . .

EXPOSE 8000

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]