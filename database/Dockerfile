# The crowded-database image: Flyway with the migrations and the demo data
# (ADR 0012). It runs once per deployment, before the services start, and exits.
#   docker build -t crowded/database .
FROM flyway/flyway:13.12.0-alpine AS flyway
# Only what PostgreSQL needs. The other databases' drivers and plugins are
# unused, and their dependencies carry fixable HIGH vulnerabilities that the
# image scan rejects (ADR 0011).
RUN cd /flyway \
    && rm -rf jre drivers/*/ lib/aad lib/netty \
    && find drivers -maxdepth 1 -type f ! -name 'postgresql-*.jar' -delete \
    && find lib/flyway \( -name 'flyway-database-*' -o -name 'flyway-gcp-*' -o -name 'flyway-firebird-*' \
      -o -name 'flyway-mysql-*' -o -name 'flyway-locations-s3-*' \) \
      ! -name 'flyway-database-postgresql-*' -delete

# The same JRE as the backend services.
FROM eclipse-temurin:25-jre-alpine
# bash runs Flyway's launcher. Alpine security fixes published after the base
# image was built (ADR 0011).
RUN apk upgrade --no-cache && apk add --no-cache bash \
    && addgroup -S crowded && adduser -S -G crowded crowded
COPY --from=flyway /flyway/ /flyway/
COPY docker-entrypoint.sh /usr/local/bin/
COPY migrations/ /flyway/migrations/
COPY seed/ /flyway/seed/
# Executable whatever the checkout's file modes (Windows has none). Also
# without BuildKit, which the integration tests' image build does not use.
RUN chmod 0755 /usr/local/bin/docker-entrypoint.sh /flyway/flyway
ENV PATH="/flyway:${PATH}"
# No usage reports to Redgate.
ENV REDGATE_DISABLE_TELEMETRY=true
USER crowded
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["migrate"]
