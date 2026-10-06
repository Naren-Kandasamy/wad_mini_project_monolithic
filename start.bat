@echo off
setlocal enabledelayedexpansion

title Shopping Cart Modular Monolith - Local Environment

cd /d "%~dp0"

echo ==================================================================
echo     Starting Shopping Cart Modular Monolith Local Environment
echo ==================================================================

:: ── 1. Check Docker & MongoDB Replica Set on Port 27018 ─────────────
echo.
echo [1/4] Checking MongoDB replica set on port 27018...

where docker >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Docker was not found on your PATH.
    echo Please ensure Docker Desktop for Windows is installed and running.
    echo Download: https://www.docker.com/products/docker-desktop/
    pause
    exit /b 1
)

:: Launch or ensure mongodb container via docker compose
docker compose -f infra\docker-compose.yml up -d mongodb
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Failed to start MongoDB container via Docker Compose.
    pause
    exit /b 1
)

:: Determine running container name (shopping-cart-mongo or mongo-test-rs)
set "MONGO_CONTAINER=shopping-cart-mongo"
docker ps --format "{{.Names}}" | findstr /i "mongo-test-rs" >nul
if %ERRORLEVEL% EQU 0 (
    set "MONGO_CONTAINER=mongo-test-rs"
)

echo Waiting for MongoDB replica set rs0 readiness in container '!MONGO_CONTAINER!'...
set RETRIES=30
:WAIT_MONGO
docker exec !MONGO_CONTAINER! mongosh --port 27018 --quiet --eval "rs.status().ok" 2>nul | findstr "1" >nul
if %ERRORLEVEL% EQU 0 (
    echo [OK] MongoDB replica set rs0 is ready on port 27018.
    goto MONGO_READY
)
set /a RETRIES-=1
if %RETRIES% LEQ 0 (
    echo [ERROR] Timed out waiting for MongoDB replica set on port 27018.
    echo Please check 'docker logs !MONGO_CONTAINER!' for details.
    pause
    exit /b 1
)
timeout /t 1 /nobreak >nul
goto WAIT_MONGO

:MONGO_READY

:: ── 2. Check / Build Backend Spring Boot Artifact ────────────────────
echo.
echo [2/4] Checking Backend Spring Boot artifact...
set "JAR_FILE=backend\target\backend-0.0.1-SNAPSHOT.jar"

if not exist "!JAR_FILE!" (
    echo Backend artifact not found. Compiling JAR (skipping tests for fast startup)...
    where mvn >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        call mvn -f backend\pom.xml package -DskipTests
    ) else (
        echo Global 'mvn' not detected; using backend\mvnw.cmd wrapper...
        cd backend
        call mvnw.cmd package -DskipTests
        cd ..
    )
    if not exist "!JAR_FILE!" (
        echo [ERROR] Backend compilation failed. Please verify Java 21 is installed.
        pause
        exit /b 1
    )
    echo [OK] Backend JAR compiled successfully.
) else (
    echo [OK] Found existing artifact: !JAR_FILE!
)

:: ── 3. Check Frontend Dependencies ──────────────────────────────────
echo.
echo [3/4] Checking Frontend dependencies...
where npm >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Node.js / npm was not found on your PATH.
    echo Please install Node.js (v18 or newer) from https://nodejs.org/
    pause
    exit /b 1
)

if not exist "frontend\node_modules\" (
    echo Installing frontend npm packages...
    cd frontend
    call npm install
    cd ..
    echo [OK] Frontend packages installed.
) else (
    echo [OK] Frontend node_modules present.
)

:: ── 4. Launch Backend and Frontend in Dedicated Windows ──────────────
echo.
echo [4/4] Launching services...

echo Starting Spring Boot API on port 8080 in a separate console window...
start "Shopping Cart - Spring Boot API (Port 8080)" cmd /k "cd /d "%~dp0" && java -jar -Dspring.profiles.active=dev !JAR_FILE!"

echo Waiting for Spring Boot API health check on port 8080...
set RETRIES=35
:WAIT_BACKEND
powershell -NoProfile -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200 -and $r.Content -match 'UP') { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Spring Boot API is healthy.
    goto BACKEND_READY
)
set /a RETRIES-=1
if %RETRIES% LEQ 0 (
    echo [INFO] Spring Boot backend is starting up. Check the API window for live initialization logs.
    goto BACKEND_READY
)
timeout /t 1 /nobreak >nul
goto WAIT_BACKEND

:BACKEND_READY

echo Starting Vue 3 Vite dev server on port 5173 in a separate console window...
start "Shopping Cart - Vue 3 Frontend (Port 5173)" cmd /k "cd /d "%~dp0frontend" && npm run dev"

echo.
echo ==================================================================
echo     ALL SERVICES OPERATIONAL AND READY FOR LOCAL TESTING!
echo ==================================================================
echo   Frontend UI:       http://localhost:5173
echo   Backend API:       http://localhost:8080/api
echo   Actuator Health:   http://localhost:8080/actuator/health
echo   Admin Stats:       http://localhost:8080/api/admin/stats
echo   MongoDB Replica:   localhost:27018 (rs0)
echo ==================================================================
echo.
echo   PRE-SEEDED CREDENTIALS FOR TESTING:
echo   - Customer:   user1  / password123  (Role: USER)
echo   - Admin:      admin1 / admin123     (Role: ADMIN, USER)
echo   - Developer:  dev1   / dev123       (Role: DEVELOPER, USER)
echo ==================================================================
echo.
echo [INFO] Service consoles are running in separate dedicated windows.
echo To stop all services at any time, run 'stop.bat' or close the terminal windows.
echo.
pause
