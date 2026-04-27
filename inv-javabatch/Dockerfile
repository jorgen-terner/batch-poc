FROM image-registry.openshift-image-registry.svc:5000/cap-template/openjdk-21-runtime:21.0.8.0.9-1-fk2



WORKDIR /deployments



ENV LANG=sv_SE.UTF-8

ENV LANGUAGE=sv_SE:sv

ENV LC_ALL=sv_SE.UTF-8



ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Duser.language=sv -Duser.country=SE"



USER 1001



# Här kan man tänka sig att "inf-javabatch-job" ligger i en rpm-fil som laddas hem alternativt att gradle laddar ner quarkus-app-jarfilen direkt.

ARG QUARKUS_APP_DIR=inf-javabatch/build/quarkus-app

COPY --chown=1001 ${QUARKUS_APP_DIR}/lib/ /deployments/lib/

COPY --chown=1001 ${QUARKUS_APP_DIR}/*.jar /deployments/

COPY --chown=1001 ${QUARKUS_APP_DIR}/app/ /deployments/app/

COPY --chown=1001 ${QUARKUS_APP_DIR}/quarkus/ /deployments/quarkus/

RUN test -r /deployments/quarkus-run.jar



COPY --chown=1001:0 inv-javabatch/inv-batch.sh /usr/local/bin/inv-batch.sh
COPY --chown=1001:0 inv-javabatch/preinit.sh /usr/local/bin/preinit.sh

RUN chmod +x /usr/local/bin/inv-batch.sh
RUN chmod +x /usr/local/bin/preinit.sh



USER 0

RUN \

  microdnf install --nodocs -y --disableplugin=subscription-manager \

  dnf



RUN dnf install https://dev.repo.de/repository/rpm-feature/batchapi_jenkinsfile_1777015020.x86_64.rpm --nodocs -y --disableplugin=subscription-manager



USER 1001

CMD bash -c "/produkter/tools/poc_batch_api/bin/batch-wrapper.sh /usr/local/bin/preinit.sh"