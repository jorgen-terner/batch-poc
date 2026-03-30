package com.batchjobapp.service;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class JobCompletionWatcher {
    private final KubernetesClient client;
    private final JobPhaseResolver phaseResolver;

    @Inject
    public JobCompletionWatcher(KubernetesClient client, JobPhaseResolver phaseResolver) {
        this.client = client;
        this.phaseResolver = phaseResolver;
    }

    public void awaitCompletion(String namespace, String jobName, long resyncMillis, Long timeoutSeconds) {
        CountDownLatch completionLatch = new CountDownLatch(1);
        AtomicReference<String> terminalPhase = new AtomicReference<>();
        AtomicReference<Exception> handlerError = new AtomicReference<>();

        SharedInformerFactory informerFactory = client.informers();
        SharedIndexInformer<Job> jobInformer = informerFactory.sharedIndexInformerFor(Job.class, resyncMillis);
        jobInformer.addEventHandler(new ResourceEventHandler<>() {
            @Override
            public void onAdd(Job job) {
                onJobEvent(job);
            }

            @Override
            public void onUpdate(Job oldJob, Job newJob) {
                onJobEvent(newJob);
            }

            @Override
            public void onDelete(Job job, boolean deletedFinalStateUnknown) {
                onJobEvent(job);
            }

            private void onJobEvent(Job job) {
                try {
                    if (!isTargetJob(job, namespace, jobName)) {
                        return;
                    }
                    String phase = phaseResolver.resolvePhase(job);
                    if (phaseResolver.isTerminalPhase(phase)) {
                        terminalPhase.compareAndSet(null, phase);
                        completionLatch.countDown();
                    }
                } catch (Exception ex) {
                    handlerError.compareAndSet(null, ex);
                    completionLatch.countDown();
                }
            }
        });

        try {
            informerFactory.startAllRegisteredInformers();

            boolean completed;
            if (timeoutSeconds == null) {
                completionLatch.await();
                completed = true;
            } else {
                completed = completionLatch.await(timeoutSeconds, TimeUnit.SECONDS);
            }

            if (handlerError.get() != null) {
                throw new IllegalStateException("Failed while handling Job watch event", handlerError.get());
            }
            if (!completed) {
                throw new IllegalStateException(
                    "Timed out waiting for job to reach terminal state: " + namespace + "/" + jobName
                );
            }

            String terminal = terminalPhase.get();
            if (terminal == null) {
                throw new IllegalStateException("Job watch ended without a terminal state for " + namespace + "/" + jobName);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for job to complete", ex);
        } finally {
            informerFactory.stopAllRegisteredInformers();
        }
    }

    private boolean isTargetJob(Job job, String namespace, String jobName) {
        if (job == null || job.getMetadata() == null) {
            return false;
        }
        return namespace.equals(job.getMetadata().getNamespace()) && jobName.equals(job.getMetadata().getName());
    }
}
