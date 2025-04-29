/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.executor.Executor;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class LocalExecutor implements Executor {

    private String commandDataOutput;
    private int exitCode;
    @Override
    public CommandResult executeCommand(String... cmd) {
        return executeCommand(Duration.ofSeconds(60).toSeconds(), cmd);
    }

    @Override
    public CommandResult executeCommand(long maxExecSeconds, String... cmd) {
        return executeCommand(maxExecSeconds, null, cmd);
    }

    public CommandResult executeCommand(long maxExecSeconds, File directory, String... cmd) {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.redirectErrorStream(false); // Separate stdout and stderr

        if (directory != null) {
            builder.directory(directory);
            Map<String, String> env = builder.environment();
            LOGGER.debug("echo PATH={}", Environment.get().getEnvironmentDefaultPath());
            env.put("PATH", Environment.get().getEnvironmentDefaultPath());
        }

        ExecutorService executor = Executors.newFixedThreadPool(2); // One for stdout, one for stderr
        Process process = null;
        try {
            process = builder.start();

            // Start reading stdout
            Process finalProcess = process;
            Future<String> stdoutFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getInputStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            });

            // Start reading stderr
            Future<String> stderrFuture = executor.submit(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(finalProcess.getErrorStream()))) {
                    return reader.lines().collect(Collectors.joining("\n"));
                }
            });

            boolean finished = process.waitFor(maxExecSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                // Wait for the process to exit after forcibly destroying it
                process.waitFor();
                LOGGER.error("[CMD] Process timed out {}\n. Stdout: {}\nStderr: {}",
                        (directory == null ? "" : directory) + String.join(" ", cmd),
                        stdoutFuture.get(), stderrFuture.get());
                throw new RuntimeException("Command timed out after " + maxExecSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            String stdout = stdoutFuture.get();
            String stderr = stderrFuture.get();

            return new CommandResult(exitCode, stdout, stderr);

        } catch (Exception e) {
            if (process != null) {
                LOGGER.error("[CMD] Exited with error: {}\n{}", process.exitValue(), process.getErrorStream());
            }
            throw new RuntimeException("Command execution failed", e);
        } finally {
            executor.shutdownNow();
        }
    }


    @Override
    public void execBackgroundCommand(String... cmd) {
        throw new ClaireNotImplementedException("Not implemented yet");
    }

    @Override
    public boolean isBackgroundCommandFinished() {
        throw new ClaireNotImplementedException("Not implemented yet");
    }

    @Override
    public String getBackgroundCommandData(int waitTime) {
        throw new ClaireNotImplementedException("Not implemented yet");
    }

    @Override
    public String getCommandData(long timeout) {
        return commandDataOutput;
    }
}

class ExecutorRunnable implements Callable {
    private InputStream inputStream;

    public ExecutorRunnable(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public Object call() throws Exception {
        return new BufferedReader(new InputStreamReader(inputStream)).lines().collect(Collectors.joining("\n"));
    }
}
