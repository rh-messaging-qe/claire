/*
 * Copyright Strimzi and Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire;

import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.executor.Executor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


public class LocalExecutor implements Executor {

    private String commandDataOutput;
    private int exitCode;
    @Override
    public Object executeCommand(String... cmd) {
        return executeCommand(Duration.ofSeconds(60).toSeconds(), cmd);
    }

    @Override
    public Object executeCommand(long maxExecMs, String... cmd) {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(new File(System.getProperty("user.home")));
        try {
            Process process = builder.start();
            StreamGobbler streamGobbler = new StreamGobbler(process.getInputStream(), System.out::println);
            streamGobbler.run();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(streamGobbler);
            exitCode = process.waitFor();
            executor.shutdown();
            commandDataOutput = streamGobbler.toString();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return exitCode;
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

class StreamGobbler implements Runnable {
    private InputStream inputStream;
    private Consumer<String> consumer;

    public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
        this.inputStream = inputStream;
        this.consumer = consumer;
    }

    @Override
    public void run() {
        new BufferedReader(new InputStreamReader(inputStream)).lines().forEach(consumer);
    }
}