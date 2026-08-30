# --- Build stage ---
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build -- --configuration production

# --- Runtime stage ---
FROM nginx:1.27-alpine AS runtime
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
# The official nginx image envsubst's every *.template file in /etc/nginx/templates/ into
# /etc/nginx/conf.d/ at container start. NGINX_ENVSUBST_FILTER restricts that substitution to
# just $BACKEND_URL - without it, envsubst would also blank out nginx's own $host/$remote_addr/
# etc. (they look like shell variables too, and are unset in the container's actual environment).
ENV NGINX_ENVSUBST_FILTER=^BACKEND_URL$
COPY nginx.conf.template /etc/nginx/templates/default.conf.template

EXPOSE 80
