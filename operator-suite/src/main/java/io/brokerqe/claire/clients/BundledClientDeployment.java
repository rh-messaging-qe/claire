/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.executor.Executor;
import io.brokerqe.claire.executor.ExecutorOperator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BundledClientDeployment implements KubernetesDeployableClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(BundledClientDeployment.class);
    private final String namespace;
    private Pod pod;

    public BundledClientDeployment(String namespace) {
        this.namespace = namespace;
    }

    public BundledClientDeployment(String namespace, Pod pod) {
        this.namespace = namespace;
        this.pod = pod;
    }

    @Override
    public Pod getContainer() {
        if (pod == null) {
            LOGGER.debug("[{}] [BundledClient] Using default first found artemis pod", namespace);
            pod = ResourceManager.getKubeClient().getArtemisPodByLabel(namespace);
        }
        return pod;
    }

    @Override
    public String getContainerName() {
        return pod.getMetadata().getName();
    }

    @Override
    public void setContainer(Pod pod) {
        this.pod = pod;
    }

    @Override
    public Executor getExecutor() {
        return new ExecutorOperator(getContainer());
    }

    @Override
    public String getExecutableHome() {
        return "./amq-broker/bin";
    }

    @Override
    public Deployment deployContainer() {
        // We do not perform deployment of this container. It is already deployed by brokerPod
        return getDeployment();
    }

    @Override
    public void undeployContainer() {
        throw new UnsupportedOperationException("Undeployment of broker pod is not in this class's responsibility");
    }

    @Override
    public Deployment deployContainer(boolean secured, List<String> secretNames) {
        // We do not perform deployment of this container. It is already deployed by brokerPod
        return getDeployment();
    }

    @Override
    public String createFile(String name, int size, String unit) {
        throw new ClaireNotImplementedException("Creating files in Pods not yet implemented!");
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public Deployment getDeployment() {
        // TODO ??!
        return ResourceManager.getKubeClient().getDeployment(namespace, pod.getMetadata().getGenerateName());
//        throw new UnsupportedOperationException("Statefulset of broker pod is not needed for clients pod");
    }
}
