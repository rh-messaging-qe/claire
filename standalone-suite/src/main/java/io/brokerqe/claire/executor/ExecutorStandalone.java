/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.executor;

import io.brokerqe.claire.CommandResult;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ExecutorStandalone implements Executor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorStandalone.class);
    private final GenericContainer container;
    private Container.ExecResult execResult;
    private CompletableFuture<Container.ExecResult> subscriberCompletableFuture;

    public <T extends GenericContainer> ExecutorStandalone(T container) {
        this.container = container;
    }

    @Override
    public CommandResult executeCommand(String... command) {
        return executeCommand(Constants.DURATION_30_SECONDS, command);
    }

    public CommandResult executeCommand(long maxExecMs, String... command) {
        LOGGER.debug("[{}] Executing command {}", container.getContainerName(), String.join(" ", command));
        try {
            Container.ExecResult execResult = container.execInContainer(command);
            int cmdReturnCode = execResult.getExitCode();
            if (cmdReturnCode != 0) {
                String errMsg = String.format("Error on executing command '%s' in container %s, return code: %s\n%s",
                        String.join(" ", command), container.getContainerName(), cmdReturnCode, execResult.getStderr());
                LOGGER.error("[ExecutorStandalone] {}", errMsg);
                throw new ClaireRuntimeException(execResult.getStderr(), new Throwable(errMsg));
            }
            return new CommandResult(execResult.getExitCode(), execResult.getStdout(), execResult.getStderr());
        } catch (IOException | InterruptedException e) {
            String errMsg = String.format("Error on executing command '%s' in container %s: %s",
                    String.join(" ", command), container.getContainerName(), e.getMessage());
            LOGGER.error(errMsg);
            throw new ClaireRuntimeException(errMsg, e);
        }
    }

    @Override
    public void execBackgroundCommand(String... command) {
        LOGGER.debug("[{}] Executing background command {}", container.getContainerName(), String.join(" ", command));
        subscriberCompletableFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return container.execInContainer(command);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Override
    public boolean isBackgroundCommandFinished() {
        return subscriberCompletableFuture.isDone();
    }


    @Override
    public String getBackgroundCommandData(int timeout) {
        TestUtils.waitFor("Subscriber thread to finish", Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE, this::isBackgroundCommandFinished);
        try {
            execResult = subscriberCompletableFuture.get();
            return getCommandData();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getCommandData() {
        return getCommandData(Constants.DURATION_5_SECONDS);
    }
    @Override
    public String getCommandData(long timeout) {
        String output;
        if (execResult.getExitCode() == 0) {
            output = execResult.getStdout();
        } else {
            output = execResult.getStderr();
        }
        LOGGER.debug(execResult.getStdout());
        LOGGER.debug(execResult.getStderr());
        return output;
    }
}