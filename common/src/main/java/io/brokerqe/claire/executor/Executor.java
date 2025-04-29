/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.executor;

import io.brokerqe.claire.CommandResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Executor {

    Logger LOGGER = LoggerFactory.getLogger(Executor.class);
    CommandResult executeCommand(String... cmd);
    CommandResult executeCommand(long maxExecMs, String... cmd);
    void execBackgroundCommand(String... cmd);
    boolean isBackgroundCommandFinished();
    String getBackgroundCommandData(int waitTime);
    String getCommandData(long timeout);

}