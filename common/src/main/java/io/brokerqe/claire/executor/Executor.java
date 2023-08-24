/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.executor;

public interface Executor {

    Object executeCommand(String... cmd);
    Object executeCommand(long maxExecMs, String... cmd);
    void execBackgroundCommand(String... cmd);
    boolean isBackgroundCommandFinished();
    String getBackgroundCommandData(int waitTime);
    String getCommandData(long timeout);

}