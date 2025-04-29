/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.executor;

import io.brokerqe.claire.CommandResult;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
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
public class ExecutorOperator implements AutoCloseable, Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorOperator.class);

    private final KubernetesClient client;
    private SimpleListener listener;
    private ExecWatch execWatch;
    private Pod pod;

    public ExecutorOperator() {
        client = ResourceManager.getKubeClient().getKubernetesClient();
    }

    public ExecutorOperator(Pod pod) {
        client = ResourceManager.getKubeClient().getKubernetesClient();
        this.pod = pod;
    }

    public String getCommandData(long timeout) {
        try {
            return listener.data.get(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void setExecutorWatcher(ExecWatch watcher) {
        this.execWatch = watcher;
    }

    public ExecWatch getExecutorWatcher() {
        return execWatch;
    }

    @Override
    public void close() {
        if (listener.data == null || listener.data.isDone()) {
//            client.close();
            LOGGER.trace("We should close client, but we're not. (Reusing singleton KubernetesClient)");
        } else {
            LOGGER.trace("Not yet closing client");
        }
    }

    @Override
    public CommandResult executeCommand(String... cmd) {
        return executeCommand(Constants.DURATION_30_SECONDS, cmd);
    }

    @Override
    public CommandResult executeCommand(long maxExecMs, String... cmd) {
        storeCommand(cmd);
        LOGGER.debug("[{}] {} Running command: {}", pod.getMetadata().getNamespace(), pod.getMetadata().getName(),
                String.join(" ", cmd));

        CompletableFuture<String> data = new CompletableFuture<>();
        try (ExecWatch execWatch = execCmdOnPod(pod, data, cmd)) {
            return new CommandResult(0, data.get(maxExecMs, TimeUnit.MILLISECONDS), null);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            LOGGER.error("Failed to finish execution in time!");
            return null;
        }
    }

    @Override
    public void execBackgroundCommand(String... cmd) {
        storeCommand(cmd);
        LOGGER.info("[{}] {} Running background command: {}", pod.getMetadata().getNamespace(),
                pod.getMetadata().getName(), Arrays.toString(cmd).replaceAll(",", ""));
        CompletableFuture<String> data = new CompletableFuture<>();
        setExecutorWatcher(execCmdOnPod(pod, data, cmd));
    }

    @Override
    public boolean isBackgroundCommandFinished() {
        return getExecutorWatcher().exitCode().isDone();
    }

    private String getBackgroundData() {
        String cmdOutput = null;
        if (isBackgroundCommandFinished()) {
            cmdOutput = getCommandData(Constants.DURATION_10_SECONDS);
            close();
        }
        return cmdOutput;
    }

    @Override
    public String getBackgroundCommandData(int waitSeconds) {
        String cmdOutput;
        while ((cmdOutput = getBackgroundData()) == null) {
            LOGGER.debug("Waiting for command to finish (checking every {}s)", waitSeconds);
            TestUtils.threadSleep(waitSeconds * 1000L);
        }
        LOGGER.debug(cmdOutput);
        return cmdOutput;
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

    private void storeCommand(String[] cmd) {
        if (ResourceManager.getEnvironment().isSerializationEnabled()) {
            String commandToLog = String.format("%s [%s]-[%s]: %s", TestUtils.generateTimestamp(), pod.getMetadata().getNamespace(), pod.getMetadata().getName(), String.join(" ", cmd));
            DataStorer.storeCommand(commandToLog);
        }
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
            LOGGER.trace("Opened executor client, waiting for data... ");
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            LOGGER.error("Failed with {} message {}", t.getCause(), t.getMessage());
            data.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            LOGGER.trace("Exit with: " + code + " and with reason: " + reason);
            data.complete(baos.toString());
        }
    }

}