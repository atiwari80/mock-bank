# Build context is the REPO ROOT, so /db migrations can be baked onto the classpath.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY middleware/pom.xml ./pom.xml
RUN mvn -B -q dependency:go-offline

COPY middleware/src ./src
# Single source of truth for migrations is /db/migrations.
COPY db/migrations ./src/main/resources/db/migration
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/middleware-0.1.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
