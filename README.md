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
  extraction, OpenAI for field parsing with an offline
  spaCy + regex fallback, OpenCV-heuristic logo crop.
- **Database** — MongoDB.

## Google login setup

Google sign-in uses Spring Security's OAuth2 login flow. To make it work, set
these environment variables for the backend:

- `GOOGLE_CLIENT_ID`
- `GOOGLE_CLIENT_SECRET`

In Google Cloud Console, create an OAuth 2.0 Client ID for a **Web application**
and add this redirect URI:

- `http://localhost:8080/login/oauth2/code/google`

If you prefer to keep the Vite proxy pointing at `127.0.0.1`, also add:

- `http://127.0.0.1:8080/login/oauth2/code/google`

If you run the frontend in Docker or with the Vite dev server, the login button
still starts the flow through the backend at `/oauth2/authorization/google`.
If these values are missing, Google will show `invalid_client` / “OAuth client
was not found” instead of logging you in.

After Google returns to the app, it now lands on `/auth-callback`, refreshes
the logged-in user, and then forwards you into the main app.

When using `docker-compose`, startup will now fail fast if either Google value is
missing, so you don’t end up debugging a fake login that only looks healthy.

## Cost

Everything below runs locally, except the OpenAI API usage you choose to send:

- OpenAI API — the ML service automatically falls back to spaCy + regex if
  `OPENAI_API_KEY` is unset or the call fails/hits a rate limit, so nothing
  breaks without it.
- MongoDB — local Docker runs use the bundled `mongo` service by default;
  if you run the backend directly, set `SPRING_DATA_MONGODB_URI` to a valid
  Atlas or self-hosted URI and keep `SPRING_DATA_MONGODB_DATABASE=cardanalyzer`.
- EasyOCR, spaCy, OpenCV — open source, run locally inside the ml-service
  container, no API cost.

## Running locally

```bash
# edit .env and ml-service/.env to add OPENAI_API_KEY plus your MongoDB and Google OAuth values
docker-compose up --build
```

- Frontend: http://localhost:3000
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
- Wire `OPENAI_API_KEY` in as a Kubernetes secret when you deploy to EKS.
