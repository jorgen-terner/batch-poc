FROM eclipse-temurin:21-jre

WORKDIR /app

COPY build/libs/inv-batch-test-app.jar /app/inv-batch-test-app.jar
COPY run-inv-batch-test-app.sh /usr/local/bin/run-inv-batch-test-app.sh

RUN mkdir -p /opt/inf-javabatch-runner && \
    chmod +x /usr/local/bin/run-inv-batch-test-app.sh

EXPOSE 8080

ENTRYPOINT ["/usr/local/bin/run-inv-batch-test-app.sh"]
