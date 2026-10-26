# Serves the Flutter web build with NGINX as a non-root user.
# Build the app first (CI does this with a pinned Flutter version):
#   flutter build web --release
#   docker build -t crowded/participant-app .
FROM nginxinc/nginx-unprivileged:1.31.7-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY build/web /usr/share/nginx/html

USER 101
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/healthz || exit 1
