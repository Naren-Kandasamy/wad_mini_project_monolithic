# Deployment Guide: Vercel (Frontend) & Render (Backend)

This guide provides step-by-step instructions for deploying the **Aura & Earth Hardware Modular Monolith**:
- **Frontend (Vue 3 + Vite)** on **Vercel**
- **Backend (Spring Boot 3.4 / 4.1.1 + MongoDB)** on **Render**

---

## 1. Deploying Backend to Render (For Your Teammate)

Render will build and run the Spring Boot service using the root [Dockerfile](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Dockerfile) or the [render.yaml](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/render.yaml) Blueprint.

### Option A: 1-Click Render Blueprint (Recommended)
1. In Render, click **New +** -> **Blueprint**.
2. Connect your GitHub repository (`wad_mini_project_monolithic`).
3. Render will detect `render.yaml` and configure the web service automatically.
4. Set the required environment variables:
   - `MONGODB_URI`: Your MongoDB Atlas connection string (e.g., `mongodb+srv://user:pass@cluster.mongodb.net/shopping_cart?retryWrites=true&w=majority`).
   - `CORS_ALLOWED_ORIGINS`: `http://localhost:5173,https://*.vercel.app` (or your specific Vercel production URL).
5. Click **Apply**. Render will build the Docker container and start your service.

### Option B: Manual Web Service Setup
1. In Render Dashboard, click **New +** -> **Web Service**.
2. Select repository and branch `main`.
3. Choose **Docker** as the Runtime.
4. Set **Health Check Path** to `/actuator/health`.
5. Under **Environment Variables**, add:
   | Key | Value | Description |
   | :--- | :--- | :--- |
   | `PORT` | `8080` (Render sets this automatically) | Internal listening port |
   | `SPRING_PROFILES_ACTIVE` | `prod` | Production Spring profile |
   | `MONGODB_URI` | `mongodb+srv://...` | MongoDB database connection |
   | `CORS_ALLOWED_ORIGINS` | `https://*.vercel.app,http://localhost:5173` | Allowed frontend domains |
   | `KEYCLOAK_ISSUER_URI` | `http://localhost:8180/realms/shopping-cart` | Optional if using external Keycloak |

6. Once deployed, note your Render URL: `https://<your-service-name>.onrender.com`.

---

## 2. Deploying Frontend to Vercel (For You)

The frontend is a Vue 3 Single Page Application (SPA). `vercel.json` is pre-configured with SPA catch-all rewrites so page reloads (`/cart`, `/orders`, `/admin`) never result in 404 errors.

### Step-by-Step Vercel Setup:
1. Go to [Vercel Dashboard](https://vercel.com/dashboard) and click **Add New...** -> **Project**.
2. Import the `wad_mini_project_monolithic` GitHub repository.
3. In **Project Configuration**:
   - **Framework Preset**: Vite
   - **Root Directory**: `frontend` (or leave as root; both have `vercel.json` configured)
   - **Build Command**: `npm run build` (or `vue-tsc && vite build`)
   - **Output Directory**: `dist`
4. Under **Environment Variables**, add:
   | Key | Value | Description |
   | :--- | :--- | :--- |
   | `VITE_API_BASE_URL` | `https://<your-service-name>.onrender.com` | Your teammate's deployed Render URL |
   | `VITE_KEYCLOAK_URL` | *(Optional)* | Hosted Keycloak URL or leave blank for local fallback |

5. Click **Deploy**.
6. When deployment finishes, Vercel gives you your live URL (e.g., `https://wad-mini-project.vercel.app`).
7. Give this URL to your teammate so they can verify that `CORS_ALLOWED_ORIGINS` on Render includes your domain.

---

## 3. Deploying Keycloak to Render (Remote Auth Server)

We have created [`Dockerfile.keycloak`](file:///home/nkandasamy/Desktop/wad_mini_project_monolithic/Dockerfile.keycloak) which automatically packages the pre-seeded realm, clients, and demo accounts (`user1`, `admin1`, `dev1`).

### Option A: Using the Render Blueprint
If your teammate deploys using `render.yaml`, Render automatically detects and sets up `shopping-cart-keycloak` alongside the backend service!

### Option B: Manual Web Service Setup
1. In Render Dashboard, click **New +** -> **Web Service**.
2. Connect your GitHub repository (`wad_mini_project_monolithic`).
3. Set the service properties:
   - **Name**: `shopping-cart-keycloak`
   - **Runtime**: Docker
   - **Dockerfile Path**: `./Dockerfile.keycloak`
   - **Health Check Path**: `/health/ready`
4. Under **Environment Variables**, add:
   | Key | Value | Description |
   | :--- | :--- | :--- |
   | `KC_BOOTSTRAP_ADMIN_USERNAME` | `admin` | Keycloak admin username |
   | `KC_BOOTSTRAP_ADMIN_PASSWORD` | `admin` | Keycloak admin password |
   | `KC_HOSTNAME_STRICT` | `false` | Allows Render domain without SSL hostname errors |
   | `KC_HTTP_ENABLED` | `true` | Allows Render reverse-proxy HTTP traffic |
   | `KC_PROXY_HEADERS` | `xforwarded` | Reads HTTPS headers from Render edge |

5. Click **Create Web Service**. Once deployed, your Keycloak URL will be:
   `https://<your-keycloak-app>.onrender.com`
6. Connect it to your other services:
   - **On Vercel (Frontend)**: Set `VITE_KEYCLOAK_URL` to `https://<your-keycloak-app>.onrender.com`
   - **On Render (Backend)**: Set `KEYCLOAK_ISSUER_URI` to `https://<your-keycloak-app>.onrender.com/realms/shopping-cart`

---

## 4. Verifying the Full Stack Live

1. Open your Vercel URL in your browser.
2. Observe the animated PCB circuit board canvas in the background with electrical bus pulses.
3. Test **Email Login**:
   - Click **Sign In**.
   - Enter `user1@example.com` with password `password123`.
   - Verify that your user session initializes as `user1 (USER)`.
4. Add items to basket from the catalog and proceed to checkout.
5. In **My Orders**, inspect confirmed orders and print your cryptographic warranty slip.
