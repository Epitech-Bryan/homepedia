# syntax=docker/dockerfile:1
FROM docker.io/library/node:22-alpine AS build
RUN npm install -g pnpm
WORKDIR /app
COPY pnpm-lock.yaml pnpm-workspace.yaml package.json ./
COPY webapp/package.json webapp/
RUN pnpm install --frozen-lockfile --filter webapp
COPY webapp/ webapp/
# Issue #6 vector tiles are disabled at build time until the frontend
# rendering bug is rooted out — the backend MVT endpoint and the
# CityVectorGridLayer component stay in place, just gated off. Flip
# the ENV below back to true once browser-side debugging confirms
# polygons render correctly.
# ENV VITE_USE_VECTOR_TILES=true
RUN pnpm --filter webapp build

FROM docker.io/library/nginx:alpine
# OCI source label so GHCR auto-links this package to the source repo
# (grants Actions workflows in this repo write:packages by default).
LABEL org.opencontainers.image.source=https://github.com/Epitech-Bryan/homepedia
LABEL org.opencontainers.image.description="HomePedia webapp — Vite/React frontend served by nginx"
LABEL org.opencontainers.image.licenses=MIT
COPY --from=build /app/webapp/dist /usr/share/nginx/html
COPY webapp/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
