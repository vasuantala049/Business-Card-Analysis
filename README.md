# Business Card Analyzer

Upload or photograph a business card and get structured contact details
(name, designation, company, phone, email, website, address) plus a cropped
logo, extracted automatically.

## Stack

- **Frontend** — React + Vite. File upload or live camera capture, editable
  results form, saved-cards list.
- **Backend** — Spring Boot. Accepts the upload, sends the image straight to
  a local Qwen chat-completions endpoint, persists results to MongoDB, and
  exposes the REST API the frontend talks to.
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

Everything below runs locally, except the Qwen-compatible model endpoint you choose to connect to:

- Qwen / llama.cpp-compatible endpoint — the Spring backend sends the card
  image to `QWEN_BASE_URL` (default `http://localhost:50305/v1`) using the
  Qwen chat-completions image format. No separate ML service is required.
- MongoDB — local Docker runs use the bundled `mongo` service by default;
  if you run the backend directly, set `SPRING_DATA_MONGODB_URI` to a valid
  Atlas or self-hosted URI and keep `SPRING_DATA_MONGODB_DATABASE=cardanalyzer`.

## Running locally

```bash
# edit .env to add QWEN_BASE_URL if your Qwen server is not on localhost:50305,
# plus your MongoDB and Google OAuth values
docker-compose up --build
```

The frontend uploads the original image as binary multipart data. The Spring
Boot backend is the only place that converts the image to Base64 before sending
it to Qwen. The backend then builds the `image_url.url` data URL for the
multimodal request.

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
- Qwen server: http://localhost:50305/v1 (external service)

First build will take a while because the Spring and React images still need to
compile and install their dependencies.

## Folder structure

```
frontend/     React app — upload, camera capture, results form, card list
backend/      Spring Boot API — direct Qwen call + MongoDB persistence
```

## Next steps

- Add Spring Security + JWT to `backend/` for multi-user support.
- Add duplicate detection (fuzzy match on phone/email) before saving.
- Add vCard (.vcf) export from `CardResultForm`.
- Wire `QWEN_BASE_URL` in as a Kubernetes secret or config value when you deploy.
