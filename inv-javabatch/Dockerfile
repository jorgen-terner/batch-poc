FROM image-registry.openshift-image-registry.svc:5000/cap-template/openjdk-21-runtime:21.0.8.0.9-1

##############################################################
# Installera batch-api via rpm
##############################################################
USER 0
RUN \
  microdnf install --nodocs -y --disableplugin=subscription-manager \
  dnf

RUN dnf install https://dev.repo.de/repository/rpm-feature/inf/inf_poc_batch_api/inf_poc_batch_api-0.1.0-alpha_batchapi_jenkinsfile_1777015020.x86_64.rpm --nodocs -y --disableplugin=subscription-manager

##############################################################
# Installera javabatch-api via rpm (springbatch)
##############################################################
RUN dnf install https://dev.repo.de/repository/rpm-feature/inf/inf_poc_javabatch_api/inf_poc_javabatch_api-0.1.0-alpha_ver2_suspendes_jobs_1777375376.x86_64.rpm --nodocs -y --disableplugin=subscription-manager

##############################################################
# Installera batch-jobbet-script samt starta wrapper-script
# med batch-job-script som inparameter
##############################################################
USER 1001

ENV LANG=sv_SE.UTF-8
ENV LANGUAGE=sv_SE:sv
ENV LC_ALL=sv_SE.UTF-8

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager -Duser.language=sv -Duser.country=SE"

COPY --chown=1001:0 inv-batch.sh /usr/local/bin/inv-batch.sh
RUN chmod +x /usr/local/bin/inv-batch.sh

CMD bash -c "/produkter/tools/inf_poc_batch_api/bin/batch-wrapper.sh /usr/local/bin/inv-batch.sh"
