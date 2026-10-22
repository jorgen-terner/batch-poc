FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/inv-batch-test-app.jar /app/inv-batch-test-app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/inv-batch-test-app.jar"]
