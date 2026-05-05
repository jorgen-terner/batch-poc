FROM eclipse-temurin:21-jre

WORKDIR /app
COPY build/libs/example-springbatch-app-0.1.0.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
