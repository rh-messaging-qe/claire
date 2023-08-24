/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;

public interface KubernetesDeployableClient extends DeployableClient<Deployment, Pod> {

    String createFile(String name, int size, String unit);

    String getNamespace();

    <T extends HasMetadata, Namespaced> T getDeployment();

}
