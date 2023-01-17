/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.commons.lang.NotImplementedException;

import java.util.concurrent.TimeUnit;

public abstract class MessagingAmqpClient implements MessagingClient {

    static KubeClient kubeClient = ResourceManager.getKubeClient();

    public static Deployment deployClientsContainer(String namespace) {
        return deployClientsContainer(namespace, false, null);
    }
    public static Deployment deployClientsContainer(String namespace, boolean secured, String secretName) {
        long userId = kubeClient.getAvailableUserId(namespace, 1000L);
        Deployment deploymentSystemClients = new DeploymentBuilder()
            .withNewMetadata()
                .withName(Constants.PREFIX_SYSTEMTESTS_CLIENTS)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", Constants.PREFIX_SYSTEMTESTS_CLIENTS)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", Constants.PREFIX_SYSTEMTESTS_CLIENTS)
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
                            .withName(Constants.PREFIX_SYSTEMTESTS_CLIENTS)
                            .withImage(Constants.IMAGE_SYSTEMTEST_CLIENTS)
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

        Deployment deploymentSystemClientsSecured = new DeploymentBuilder()
            .withNewMetadata()
                .withName(Constants.PREFIX_SYSTEMTESTS_CLIENTS)
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", Constants.PREFIX_SYSTEMTESTS_CLIENTS)
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", Constants.PREFIX_SYSTEMTESTS_CLIENTS)
                    .endMetadata()
                    .withNewSpec()
                        .editOrNewSecurityContext()
                            .withRunAsNonRoot(true)
                            .withNewSeccompProfile()
                                .withType("RuntimeDefault") // localhost
                            .endSeccompProfile()
                            .withRunAsUser(userId)
                        .endSecurityContext()
                        .addNewVolume()
                            .withName("client-stores-volume")
                            .withNewSecret()
                                .withSecretName(secretName)
                                .withDefaultMode(420)
                            .endSecret()
                        .endVolume()
                        .addNewContainer()
                            .withName(Constants.PREFIX_SYSTEMTESTS_CLIENTS)
                            .withImage(Constants.IMAGE_SYSTEMTEST_CLIENTS)
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
                            .addNewVolumeMount()
                                .withName("client-stores-volume")
                                .withMountPath("/etc/ssl-stores")
                                .withReadOnly()
                            .endVolumeMount()
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        Deployment deployment;
        if (secured) {
            deployment = kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deploymentSystemClientsSecured).createOrReplace();
        } else {
            deployment = kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deploymentSystemClients).createOrReplace();
        }

        LOGGER.debug("[{}] Wait 30s for systemtest-clients deployment to be ready", namespace);
        kubeClient.getKubernetesClient().resource(deployment).inNamespace(namespace).waitUntilReady(30, TimeUnit.SECONDS);
        return deployment;
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
    public boolean compareMessages(Object sentMessages, Object receivedMessages) {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public void subscribe() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }
}
