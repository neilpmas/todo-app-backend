# syntax=docker/dockerfile:1

# Keep this in sync with pom.xml's <java.version> -- a mismatch between the build JDK
# and the pom's target release has caused subtle bytecode/runtime issues before
# (see template-improvements.md).
ARG JAVA_VERSION=24

FROM eclipse-temurin:${JAVA_VERSION}-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./
COPY src ./src

RUN ./mvnw -B package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
