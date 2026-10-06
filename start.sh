#!/usr/bin/env bash
# ==============================================================================
# Shopping Cart Modular Monolith — Local Test & Development Launcher
# ==============================================================================
# Starts:
#   1. MongoDB Replica Set (port 27018) via Docker
#   2. Spring Boot 4.1.1 Monolith API (port 8080)
#   3. Vue 3 + Vite SPA Frontend (port 5173)
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}==================================================================${NC}"
echo -e "${GREEN}    🛍️  Starting Shopping Cart Modular Monolith Local Environment${NC}"
echo -e "${BLUE}==================================================================${NC}"

# ── 1. Ensure MongoDB Replica Set on Port 27018 ────────────────────────────────
echo -e "\n${YELLOW}[1/4] Checking MongoDB replica set on port 27018...${NC}"

if docker ps --format '{{.Names}}' | grep -q "^shopping-cart-mongo$"; then
    MONGO_CONTAINER="shopping-cart-mongo"
    echo -e "${GREEN}✓ Container 'shopping-cart-mongo' is already running.${NC}"
elif docker ps --format '{{.Names}}' | grep -q "^mongo-test-rs$"; then
    MONGO_CONTAINER="mongo-test-rs"
    echo -e "${GREEN}✓ Container 'mongo-test-rs' is already running.${NC}"
elif docker ps -a --format '{{.Names}}' | grep -q "^shopping-cart-mongo$"; then
    echo -e "Starting existing container 'shopping-cart-mongo'..."
    docker start shopping-cart-mongo >/dev/null
    MONGO_CONTAINER="shopping-cart-mongo"
    echo -e "${GREEN}✓ Container 'shopping-cart-mongo' started.${NC}"
elif docker ps -a --format '{{.Names}}' | grep -q "^mongo-test-rs$"; then
    echo -e "Starting existing container 'mongo-test-rs'..."
    docker start mongo-test-rs >/dev/null
    MONGO_CONTAINER="mongo-test-rs"
    echo -e "${GREEN}✓ Container 'mongo-test-rs' started.${NC}"
else
    echo -e "Launching MongoDB container via docker-compose..."
    docker compose -f infra/docker-compose.yml up -d mongodb
    MONGO_CONTAINER="shopping-cart-mongo"
    echo -e "${GREEN}✓ MongoDB container launched.${NC}"
fi

# Wait for Mongo RS readiness
echo -n "Waiting for replica set rs0 readiness in container '$MONGO_CONTAINER'..."
RETRIES=20
until docker exec "$MONGO_CONTAINER" mongosh --port 27018 --quiet --eval "rs.status().ok" 2>/dev/null | grep -q "1" || [ $RETRIES -eq 0 ]; do
    echo -n "."
    sleep 1
    RETRIES=$((RETRIES-1))
done

if [ $RETRIES -eq 0 ]; then
    echo -e "\n${RED}✗ Timed out waiting for MongoDB replica set on port 27018.${NC}"
    exit 1
fi
echo -e " ${GREEN}Ready!${NC}"

# ── 2. Ensure Backend JAR Built ───────────────────────────────────────────────
echo -e "\n${YELLOW}[2/4] Checking Backend Spring Boot artifact...${NC}"
JAR_FILE="backend/target/backend-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo -e "Building backend JAR (skipping test suite for fast startup)..."
    mvn -f backend/pom.xml package -DskipTests
    echo -e "${GREEN}✓ Backend JAR compiled successfully.${NC}"
else
    echo -e "${GREEN}✓ Found existing artifact: $JAR_FILE${NC}"
fi

# ── 3. Ensure Frontend Dependencies Installed ─────────────────────────────────
echo -e "\n${YELLOW}[3/4] Checking Frontend dependencies...${NC}"
if [ ! -d "frontend/node_modules" ]; then
    echo -e "Installing frontend npm packages..."
    npm --prefix frontend install
    echo -e "${GREEN}✓ Frontend packages installed.${NC}"
else
    echo -e "${GREEN}✓ Frontend node_modules present.${NC}"
fi

# ── 4. Launch Backend and Frontend ────────────────────────────────────────────
echo -e "\n${YELLOW}[4/4] Launching services...${NC}"

BACKEND_LOG="/tmp/shopping-cart-backend.log"
FRONTEND_LOG="/tmp/shopping-cart-frontend.log"

# Clean up child processes on script exit or Ctrl+C
cleanup() {
    echo -e "\n\n${YELLOW}Shutting down services...${NC}"
    if [ -n "$BACKEND_PID" ]; then
        kill "$BACKEND_PID" 2>/dev/null || true
    fi
    if [ -n "$FRONTEND_PID" ]; then
        kill "$FRONTEND_PID" 2>/dev/null || true
    fi
    echo -e "${GREEN}✓ All services stopped cleanly.${NC}"
}
trap cleanup INT TERM EXIT

echo -e "Starting Spring Boot API on port 8080 (logs: $BACKEND_LOG)..."
java -jar -Dspring.profiles.active=dev "$JAR_FILE" > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

# Wait for Spring Boot to be healthy
echo -n "Waiting for Spring Boot to initialize..."
RETRIES=30
until curl -s http://localhost:8080/actuator/health | grep -q "UP" || [ $RETRIES -eq 0 ]; do
    echo -n "."
    sleep 1
    RETRIES=$((RETRIES-1))
done

if [ $RETRIES -eq 0 ]; then
    echo -e "\n${RED}✗ Spring Boot failed to report healthy within 30 seconds.${NC}"
    echo -e "Showing tail of backend logs ($BACKEND_LOG):"
    tail -n 25 "$BACKEND_LOG"
    exit 1
fi
echo -e " ${GREEN}Healthy!${NC}"

echo -e "Starting Vue 3 Vite dev server on port 5173..."
npm --prefix frontend run dev > "$FRONTEND_LOG" 2>&1 &
FRONTEND_PID=$!
sleep 2

echo -e "\n${GREEN}==================================================================${NC}"
echo -e "${GREEN}    🎉 ALL SERVICES OPERATIONAL AND READY FOR LOCAL TESTING!${NC}"
echo -e "${GREEN}==================================================================${NC}"
echo -e "  🌐 ${BLUE}Frontend UI:${NC}       ${GREEN}http://localhost:5173${NC}"
echo -e "  🔌 ${BLUE}Backend API:${NC}       ${GREEN}http://localhost:8080/api${NC}"
echo -e "  🩺 ${BLUE}Actuator Health:${NC}   ${GREEN}http://localhost:8080/actuator/health${NC}"
echo -e "  📊 ${BLUE}Admin Stats:${NC}       ${GREEN}http://localhost:8080/api/admin/stats${NC}"
echo -e "  🍃 ${BLUE}MongoDB Replica:${NC}   localhost:27018 (rs0)"
echo -e "${GREEN}==================================================================${NC}"
echo -e "${YELLOW}  Press [Ctrl+C] to stop all services and exit.${NC}\n"

# Stream backend and frontend logs or wait
tail -f "$BACKEND_LOG"
