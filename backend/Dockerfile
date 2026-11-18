FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src src
RUN --mount=type=cache,target=/root/.m2 mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN mkdir -p /data/invoices && chown -R app:app /data
USER app
ENV INVOICE_PDF_STORAGE_PATH=/data/invoices
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD wget -q -O /dev/null "http://127.0.0.1:${SERVER_PORT:-${PORT:-8080}}/actuator/health/readiness" || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/urandom", "-jar", "/app/app.jar"]
