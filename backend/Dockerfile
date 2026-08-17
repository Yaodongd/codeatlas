FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S codeatlas && adduser -S codeatlas -G codeatlas \
    && mkdir -p /app /data/repositories \
    && chown -R codeatlas:codeatlas /app /data/repositories
WORKDIR /app
COPY --from=build --chown=codeatlas:codeatlas /workspace/target/codeatlas-backend-*.jar app.jar
USER codeatlas
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]

