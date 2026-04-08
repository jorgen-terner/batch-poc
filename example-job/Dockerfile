FROM registry.access.redhat.com/ubi9/openjdk-21-runtime

USER root

COPY inf-javabatch/build/distributions/*.rpm /tmp/inf-javabatch.rpm

RUN microdnf install -y rpm \
    && rpm -ivh /tmp/inf-javabatch.rpm \
    && chmod +x /opt/inf-javabatch/bin/inf-javabatch \
    && rm -f /tmp/inf-javabatch.rpm \
    && microdnf clean all

ENTRYPOINT ["/opt/inf-javabatch/bin/inf-javabatch"]