# Business Card Analyzer

Upload or photograph a business card and get structured contact details
(name, designation, company, phone, email, website, address) plus a cropped
logo, extracted automatically.

## Stack

- **Frontend** — React + Vite. File upload or live camera capture, editable
  results form, saved-cards list.
- **Backend** — Spring Boot. Orchestrates the ML call, persists results to
  MongoDB, exposes the REST API the frontend talks to.
- **ML service** — FastAPI (Python). OpenCV preprocessing, EasyOCR text
  extraction, Gemini (free tier) for field parsing with an offline
  spaCy + regex fallback, OpenCV-heuristic logo crop.
- **Database** — MongoDB.

## Cost

Everything below runs on free tiers with no card required, except your own
hosting:

- Gemini API free tier (Flash: 10 requests/min, 250/day) — the ML service
  automatically falls back to spaCy + regex if `GEMINI_API_KEY` is unset or
  the call fails/hits a rate limit, so nothing breaks without it.
- MongoDB — local Docker runs use the bundled `mongo` service by default;
  if you run the backend directly, set `SPRING_DATA_MONGODB_URI` to a valid
  Atlas or self-hosted URI and keep `SPRING_DATA_MONGODB_DATABASE=cardanalyzer`.
- EasyOCR, spaCy, OpenCV — open source, run locally inside the ml-service
  container, no API cost.

## Running locally

```bash
cp ml-service/.env.example ml-service/.env   # optionally add GEMINI_API_KEY
cp frontend/.env.example frontend/.env
docker-compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080/api
- ML service: http://localhost:8000/health

First build will take a while — EasyOCR pulls in a CPU-only torch build and
spaCy's model gets downloaded during the image build.

## Folder structure

```
frontend/     React app — upload, camera capture, results form, card list
backend/      Spring Boot API — orchestration + MongoDB persistence
ml-service/   FastAPI — preprocessing, OCR, field extraction, logo detection
```

## Next steps

- Swap `ml-service/app/logo_detection.py`'s heuristic for a trained detector
  if you want higher precision on trickier logos.
- Add Spring Security + JWT to `backend/` for multi-user support.
- Add duplicate detection (fuzzy match on phone/email) before saving.
- Add vCard (.vcf) export from `CardResultForm`.
- Wire `GEMINI_API_KEY` in as a Kubernetes secret when you deploy to EKS.
