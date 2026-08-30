# Deployment (Render)

Render was chosen over Fly.io because Fly requires a payment method on file before it will
allocate any machine, even within free-tier usage — a hard blocker with no way to work around it
short of adding a card. Render's free tier needs no card for web services or its 90-day free
Postgres.

## One-time setup (manual — needs your Render account)

1. Sign up / log in at [render.com](https://render.com) and connect your GitHub account when
   prompted, granting it access to the `salary-management-system` repo (either "All repositories"
   or select this one specifically).
2. In the Render dashboard: **New** → **Blueprint**.
3. Select this repo. Render will detect [`render.yaml`](../render.yaml) at the repo root and show
   a preview of what it's about to create: one Postgres database (`salary-mgmt-db`) and two web
   services (`salary-mgmt-backend`, `salary-mgmt-frontend`), each built from their own Dockerfile.
4. Click **Apply** / **Create**. Render provisions the database first, then builds and deploys
   both services. First build takes a few minutes (Docker build + `npm ci` + Gradle build) — free
   tier gives them a bit less CPU, so builds run slower than the local `docker compose build`.

## After first deploy — verify the guessed URLs

`render.yaml` hardcodes `CORS_ALLOWED_ORIGINS` (on the backend) and `BACKEND_URL` (on the
frontend) as `https://salary-mgmt-backend.onrender.com` / `https://salary-mgmt-frontend.onrender.com`
— a reasonable guess, but Render's `*.onrender.com` hostnames are only guaranteed if the exact
service name isn't already taken by another Render user. Once both services are live:

1. Open each service in the Render dashboard and check its actual assigned URL (top of the
   service page).
2. If either differs from the guess above, update the corresponding env var
   (`CORS_ALLOWED_ORIGINS` on the backend, or `BACKEND_URL` on the frontend) in that service's
   **Environment** tab to the real URL, then trigger a manual redeploy of the service you changed
   (env var changes require a restart to take effect; `BACKEND_URL` also requires a redeploy since
   nginx renders it into its config at container start, not per-request).

## Smoke test

```
curl https://<frontend-url>/actuator/health   # proxied to the backend, should return {"status":"UP"}
curl -X POST https://<frontend-url>/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@acme.com","password":"<see below>"}'
```

`HR_ADMIN_PASSWORD` is set via `generateValue: true` in `render.yaml` — Render generates a random
value rather than using the `changeit` local-dev default. Find it in the backend service's
**Environment** tab in the Render dashboard before logging in for the first time (or set it to a
value of your choosing there before the first deploy).

## Known limitations of this deployment

- **Free tier services spin down after 15 minutes of inactivity** and take ~30-60s to cold-start
  on the next request — expected for a demo, not something to "fix."
- **The free Postgres database expires after 90 days.** Fine for an assessment review window; a
  real deployment would use a paid plan.
- `docker-entrypoint.sh` (backend) translates Render's single `DATABASE_URL` connection string
  into the separate `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD` Spring needs — see
  `docs/design-notes.md` if this needs revisiting for a different host.
