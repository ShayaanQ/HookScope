FROM eclipse-temurin:21.0.8_9-jdk-jammy AS build

WORKDIR /workspace
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21.0.8_9-jre-jammy

RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system hookscope \
    && useradd --system --gid hookscope --home-dir /app --create-home hookscope

WORKDIR /app
COPY --from=build /workspace/build/libs/hookscope.jar app.jar
USER hookscope:hookscope
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
