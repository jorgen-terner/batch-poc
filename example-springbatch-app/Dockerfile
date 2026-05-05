FROM eclipse-temurin:21-jre

WORKDIR /app
COPY example-springbatch-app/build/libs/example-springbatch-app-0.1.0.jar app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
