# Builds this service from source with the Maven wrapper on JDK 25 and runs it
# as a non-root user on a JRE, from Spring Boot's layered jar (ADR 0011).
#   docker build -t crowded/jobs .
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /src
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -q dependency:go-offline
COPY src/ src/
# Tests, coverage, SpotBugs and formatting already ran in CI, before this build.
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -q package -DskipTests -Dspotbugs.skip -Djacoco.skip -Dspotless.check.skip \
    && java -Djarmode=tools -jar target/jobs-*.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:25-jre-alpine
# Alpine security fixes published after the base image was built: the image
# scan fails on fixable HIGH and CRITICAL findings (ADR 0011).
RUN apk upgrade --no-cache && addgroup -S crowded && adduser -S -G crowded crowded
WORKDIR /app
# Least to most often changed, so a code change only replaces the last layer.
COPY --from=build /src/target/extracted/dependencies/ ./
COPY --from=build /src/target/extracted/spring-boot-loader/ ./
COPY --from=build /src/target/extracted/snapshot-dependencies/ ./
COPY --from=build /src/target/extracted/application/ ./
USER crowded
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
