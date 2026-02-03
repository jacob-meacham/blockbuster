FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --version
COPY src/ src/
RUN ./gradlew shadowJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/blockbuster-1.0.0-SNAPSHOT.jar app.jar
RUN mkdir -p /app/data
EXPOSE 8585 8586
ENTRYPOINT ["java", "-jar", "app.jar", "server", "/config/config.yml"]
