# Railway builds this (.railway/railway.ts); locally: docker build -t quiz-api backend
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
# tests need Docker (Testcontainers); CI runs them through ./check
RUN ./mvnw -q package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
USER nobody
ENTRYPOINT ["java", "-jar", "app.jar"]
