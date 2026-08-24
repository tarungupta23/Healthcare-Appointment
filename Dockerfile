# Multi-stage build: compiles the Spring Boot app, then runs it in a slim
# JRE-only image. You don't need to run Docker locally at all - Render
# reads this file and builds/runs the container for you automatically.

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/appointment-manager-1.0.0.jar app.jar

# Render sets $PORT dynamically; application.yml already reads SERVER_PORT
# from the environment, so this just wires the two together.
ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]