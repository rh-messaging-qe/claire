/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.executor;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

// Base code taken from kubernetes-client example
// https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-examples/src/main/java/io/fabric8/kubernetes/examples/ExecuteCommandOnPodExample.java
public class Executor implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Executor.class);

    private final KubernetesClient client;
    private SimpleListener listener;


    public Executor() {
        Config config = new ConfigBuilder().build();
        this.client = new KubernetesClientBuilder().withConfig(config).build();
    }

    public CompletableFuture<String> getListenerData() {
        return listener.data;
    }

    @Override
    public void close() {
        if (listener.data == null || listener.data.isDone()) {
            client.close();
            LOGGER.debug("Closed client");
        } else {
            LOGGER.debug("Not yet closing client");
        }
    }

    public String execCommandOnPod(String podName, String namespace, int maxExecSeconds, String... cmd) {
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        LOGGER.info("[{}] {} Running command: {}",
                namespace, pod.getMetadata().getName(), Arrays.toString(cmd).replaceAll(",", ""));

        CompletableFuture<String> data = new CompletableFuture<>();
        try (ExecWatch execWatch = execCmdOnPod(pod, data, cmd)) {
            return data.get(maxExecSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.error("Failed to finish execution in time!");
            return null;
        }
    }

    public ExecWatch execBackgroundCommandOnPod(String podName, String namespace, String... cmd) {
        Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
        LOGGER.info("[{}] {} Running background command: {}",
                namespace, pod.getMetadata().getName(), Arrays.toString(cmd).replaceAll(",", ""));

        CompletableFuture<String> data = new CompletableFuture<>();
        return execCmdOnPod(pod, data, cmd);
    }

    private ExecWatch execCmdOnPod(Pod pod, CompletableFuture<String> data, String... command) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        listener = new SimpleListener(data, baos);
        return client.pods()
                .inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName())
                .writingOutput(baos)
                .writingError(baos)
                .usingListener(listener)
                .exec(command);
    }

    static class SimpleListener implements ExecListener {

        private final CompletableFuture<String> data;
        private final ByteArrayOutputStream baos;

        public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream baos) {
            this.data = data;
            this.baos = baos;
        }

        @Override
        public void onOpen() {
            LOGGER.debug("Opened executor client, waiting for data... ");
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            LOGGER.error("Failed with {}", t.getMessage());
            data.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            LOGGER.debug("Exit with: " + code + " and with reason: " + reason);
            data.complete(baos.toString());
        }
    }

}