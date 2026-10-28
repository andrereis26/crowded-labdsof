# Builds the backoffice with Vite and serves it with NGINX as a non-root user.
# The build uses base /backoffice/, the path the router serves it at.
#   docker build -t crowded/backoffice .
FROM node:24-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
# Type check and bundle (npm run build). Tests and lint already ran in CI.
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31.7-alpine
# Alpine security fixes published after the base image was built: the image
# scan fails on fixable HIGH and CRITICAL findings (ADR 0011).
USER root
RUN apk upgrade --no-cache
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist /usr/share/nginx/html

USER 101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
