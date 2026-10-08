@echo off
echo Starting Business Card Analyzer Services...

start "Backend (Spring Boot)" cmd /k "cd backend && mvn spring-boot:run"
start "Frontend (React)" cmd /k "cd frontend && npm run dev"

echo Backend and frontend are launching in separate windows!
echo Once started, open http://localhost:5173 in your browser.
