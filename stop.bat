@echo off
setlocal enabledelayedexpansion

title Stop Shopping Cart Monolith

cd /d "%~dp0"

echo ==================================================================
echo     Stopping Shopping Cart Modular Monolith Local Environment
echo ==================================================================

echo.
echo [1/3] Stopping Spring Boot Backend (Port 8080)...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":8080" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)
echo [OK] Backend stopped.

echo.
echo [2/3] Stopping Vue 3 Frontend (Port 5173)...
for /f "tokens=5" %%a in ('netstat -aon 2^>nul ^| findstr ":5173" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)
echo [OK] Frontend stopped.

echo.
echo [3/3] Stopping MongoDB container via Docker Compose...
docker compose -f infra\docker-compose.yml down >nul 2>&1
echo [OK] Containers stopped.

echo.
echo ==================================================================
echo     All Shopping Cart services have been stopped.
echo ==================================================================
pause
