# PelaNode PoC: one image for Cloud Run (F0) and the demo laptop (F1).
# The UI stage is added in step I-01 (P0-11 section 4, plan step I-01).

FROM maven:3.9-eclipse-temurin-21 AS backend
WORKDIR /build
COPY backend/pom.xml backend/mvnw ./
COPY backend/.mvn .mvn
RUN mvn -q -B dependency:go-offline
COPY backend/src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=backend /build/target/pelanode-poc.jar app.jar
COPY data/demo /app/data/demo
ENV SPRING_PROFILES_ACTIVE=cloud \
    PN_DB_URL=jdbc:h2:file:/tmp/pelanode \
    PN_DEMO_PACK_DIR=/app/data/demo/hack-001 \
    PN_MEDIA_ROOT=/app/data/demo/hack-001/media \
    PN_EXPORT_DIR=/tmp/exports
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
