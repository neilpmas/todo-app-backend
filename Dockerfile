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

# GITHUB_PACKAGES_TOKEN is a BuildKit build secret (fly deploy --build-secret
# GITHUB_PACKAGES_TOKEN=...), mounted only for this RUN's duration -- it never lands in
# an image layer or the build cache. .mvn/docker-settings.xml wires it in as the
# credential for the two GitHub Packages repositories declared in pom.xml.
RUN --mount=type=secret,id=GITHUB_PACKAGES_TOKEN \
    export GITHUB_PACKAGES_TOKEN="$(cat /run/secrets/GITHUB_PACKAGES_TOKEN)" && \
    ./mvnw -B -s .mvn/docker-settings.xml package -DskipTests

FROM eclipse-temurin:${JAVA_VERSION}-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
