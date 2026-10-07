# syntax=docker/dockerfile:1

# ---------- Dependencies ----------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---------- Build ----------
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Rewrites are baked at build time, so the API URL must be present here.
ARG API_INTERNAL_URL=http://api:8080
ENV API_INTERNAL_URL=$API_INTERNAL_URL
ENV NEXT_STANDALONE=1
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# ---------- Runtime ----------
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

COPY --from=build --chown=node:node /app/public ./public
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static

USER node
EXPOSE 3000

HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:3000/login || exit 1

CMD ["node", "server.js"]
