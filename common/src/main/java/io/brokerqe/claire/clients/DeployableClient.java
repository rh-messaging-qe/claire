/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.brokerqe.claire.executor.Executor;

import java.util.List;

public interface DeployableClient<DeploymentT, ContainerT> {

    DeploymentT deployContainer();
    void undeployContainer();

    DeploymentT deployContainer(boolean secured, List<String> secretNames);

    ContainerT getContainer();
    void setContainer(ContainerT container);
    String getContainerName();
    Executor getExecutor();

    String getExecutableHome();

    String createFile(String name, int size, String unit);

}
