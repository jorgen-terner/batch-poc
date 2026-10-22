FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/inv-batch-test-app.jar /app/inv-batch-test-app.jar
COPY start-both.sh /usr/local/bin/start-both.sh
COPY run-inv-batch-test-app.sh /usr/local/bin/run-inv-batch-test-app.sh

RUN chmod +x /usr/local/bin/start-both.sh /usr/local/bin/run-inv-batch-test-app.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/start-both.sh"]
