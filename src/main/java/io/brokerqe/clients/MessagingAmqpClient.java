/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.clients;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.apache.commons.lang.NotImplementedException;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class MessagingAmqpClient implements MessagingClient {

    static KubeClient kubeClient = ResourceManager.getKubeClient();

    public static Deployment deployClientsContainer(String namespace) {
        return deployClientsContainer(namespace, false, null);
    }
    public static Deployment deployClientsContainer(String namespace, boolean secured, List<String> secretNames) {
        Deployment deployment;
        List<Volume> secretVolumes = new ArrayList<>();
        List<VolumeMount> secretVolumeMounts = new ArrayList<>();

        if (secured) {
            for (String secretName : secretNames) {
                secretVolumes.add(
                        new VolumeBuilder()
                            .withName(secretName + "-volume")
                            .withNewSecret()
                                .withSecretName(secretName)
                                .withDefaultMode(420)
                            .endSecret()
                        .build());
                secretVolumeMounts.add(
                        new VolumeMountBuilder()
                            .withName(secretName + "-volume")
                            .withMountPath("/etc/" + secretName)
                            .withReadOnly()
                        .build());
            }
        }

        deployment = kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(
                createSystemTestsDeployment(namespace, secured, secretVolumes, secretVolumeMounts)).createOrReplace();
        LOGGER.debug("[{}] Wait 30s for systemtest-clients deployment to be ready", namespace);
        kubeClient.getKubernetesClient().resource(deployment).inNamespace(namespace).waitUntilReady(30, TimeUnit.SECONDS);
        return deployment;
    }

    private static Deployment createSystemTestsDeployment(String namespace, boolean secured, List<Volume> secretVolumes, List<VolumeMount> secretVolumeMounts) {
        long userId = kubeClient.getAvailableUserId(namespace, 1000L);
        if (!secured) {
            return new DeploymentBuilder()
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
        } else {
            return new DeploymentBuilder()
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
                        .withVolumes(secretVolumes)
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
                            .withVolumeMounts(secretVolumeMounts)
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
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
    public Object getSentMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public Object getReceivedMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public boolean compareMessages() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }

    @Override
    public boolean compareMessages(Object sentMessagesObject, Object receivedMessagesObject) {
        if (sentMessagesObject == null || receivedMessagesObject == null) {
            return false;
        }
        List<JSONObject> sentMessages = (List<JSONObject>) sentMessagesObject;
        List<JSONObject> receivedMessages = (List<JSONObject>) receivedMessagesObject;
        return compareMessages(sentMessages, receivedMessages);
    }

    protected boolean compareMessages(List<JSONObject> sentMessages, List<JSONObject> receivedMessages) {
        // Method compares only number of sent and received messages and real comparison of messageIDs (if is present in other group)
        if (sentMessages.size() != receivedMessages.size()) {
            LOGGER.warn("Sent {} and received {} messages are not same!", sentMessages.size(), receivedMessages.size());
            return false;
        } else {
            try {
                // compare message IDs
                List<String> receivedIds = receivedMessages.stream().map(receivedMsg -> {
                    try {
                        return (String) receivedMsg.get("id");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
                for (JSONObject message : sentMessages) {
                    if (!receivedIds.contains(message.get("id"))) {
                        LOGGER.warn("Unable to find/compare messageId {}", message);
                        return false;
                    }
                }
            } catch (JSONException e) {
                LOGGER.error("Unable to parse/compare messages! {}", e.getMessage());
            }
            LOGGER.debug("All messages are same. Good.");
            return true;
        }
    }

    @Override
    public void subscribe() {
        throw new NotImplementedException("containerized clients not implemented yet");
    }
}
