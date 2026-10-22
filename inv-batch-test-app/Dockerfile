FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/inv-batch-test-app.jar /app/inv-batch-test-app.jar
COPY start-both.sh /usr/local/bin/start-both.sh

RUN chmod +x /usr/local/bin/start-both.sh

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/inv-batch-test-app.jar"]
