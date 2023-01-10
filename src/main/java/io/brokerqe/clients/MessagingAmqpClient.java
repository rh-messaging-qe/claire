/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.commons.lang.NotImplementedException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class MessagingAmqpClient implements MessagingClient {

    static Map<Deployment, String> deployedContainers = new HashMap<>();
    static KubeClient kubeClient = ResourceManager.getKubeClient();

    public static Deployment deployClientsContainer(String namespace) {
        long userId = kubeClient.getAvailableUserId(namespace, 1000L);
        Deployment deploymentSystemClients = new DeploymentBuilder()
                .withNewMetadata()
                    .withName("systemtests-clients")
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                        .addToMatchLabels("app", "systemtests-clients")
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", "systemtests-clients")
                        .endMetadata()
                        .withNewSpec()
                            .editOrNewSecurityContext()
                                .withRunAsNonRoot(true)
                                .withNewSeccompProfile()
                                    .withType("RuntimeDefault") // localhost
                                .endSeccompProfile()
                            .withRunAsUser(userId)
                            .endSecurityContext()
                            .addNewContainer()
                                .withName("systemtests-clients")
                                .withImage("quay.io/messaging/cli-java:latest")
                                .withCommand("sleep")
                                .withArgs("infinity")
                                .withImagePullPolicy("Always")
                                .editOrNewSecurityContext()
                                    .withPrivileged(false)
                                    .withAllowPrivilegeEscalation(false)
                                    .withRunAsNonRoot(true)
                                    .withNewCapabilities()
                                        .withDrop("ALL")
                                    .endCapabilities()
                                .endSecurityContext()
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();

        Deployment deployment = kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deploymentSystemClients).createOrReplace();
        LOGGER.debug("[{}] Wait 30s for systemtest-clients deployment to be ready", namespace);
        kubeClient.getKubernetesClient().resource(deployment).inNamespace(namespace).waitUntilReady(30, TimeUnit.SECONDS);
        deployedContainers.put(deployment, namespace);
        return deployment;
    }

    public static void undeployClientsContainer(String namespace, Deployment deployment) {
        kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deployment).delete();
        deployedContainers.remove(deployment);
    }

    public static void undeployAllClientsContainers() {
        LOGGER.info("Removing all deployed Messaging client containers.");
        for (Deployment deployment : deployedContainers.keySet()) {
            kubeClient.getKubernetesClient().apps().deployments().inNamespace(deployedContainers.get(deployment)).resource(deployment).delete();
        }
    }

    @Override
    public int sendMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public int receiveMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public Object getMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public boolean compareMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public void subscribe() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }
}
