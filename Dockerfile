# PelaNode PoC: one image for Cloud Run (F0) and the demo laptop (F1).
# The UI is a prebuilt static bundle in src/main/resources/static (scripts/build-ui.sh, step I-01).

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/target/pelanode-poc.jar app.jar
COPY data/demo /app/data/demo
COPY data/danube /app/data/danube
COPY data/iskar /app/data/iskar
COPY data/climate /app/data/climate
ENV SPRING_PROFILES_ACTIVE=cloud \
    PN_DB_URL=jdbc:h2:file:/tmp/pelanode \
    PN_DEMO_PACK_DIR=/app/data/demo/hack-001 \
    PN_MEDIA_ROOT=/app/data/demo/hack-001/media \
    PN_DANUBE_DIR=/app/data/danube \
    PN_CLIMATE_DIR=/app/data/climate \
    PN_EXPORT_DIR=/tmp/exports
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
