@echo off
echo Starting Business Card Analyzer Services...

start "ML Service (FastAPI)" cmd /k "cd ml-service && venv\Scripts\activate && uvicorn main:app --reload --port 8000"
start "Backend (Spring Boot)" cmd /k "cd backend && mvn spring-boot:run"
start "Frontend (React)" cmd /k "cd frontend && npm run dev"

echo All 3 services are launching in separate windows!
echo Once started, open http://localhost:5173 in your browser.
