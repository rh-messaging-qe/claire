/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.clients;

import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.exception.ClaireNotImplementedException;
import io.brokerqe.claire.executor.Executor;
import io.brokerqe.claire.executor.ExecutorOperator;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public abstract class StClientDeployment implements KubernetesDeployableClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(StClientDeployment.class);
    private final String namespace;
    static KubeClient kubeClient = ResourceManager.getKubeClient();
    private ExecutorOperator executor;
    private Pod pod;
    private Deployment deployment;

    public StClientDeployment(String namespace) {
        this.namespace = namespace;
    }

    abstract public String getPodName();
    abstract public String getContainerImageName();

    @Override
    public Pod getContainer() {
        if (pod == null) {
            pod = kubeClient.getFirstPodByPrefixName(namespace, getPodName());
            LOGGER.debug("[{}] [STClient] Using first found container {}", namespace, pod.getMetadata().getName());
        }
        return pod;
    }

    @Override
    public void setContainer(Pod pod) {
        this.pod = pod;
    }

    @Override
    public String getContainerName() {
        return pod.getMetadata().getName();
    }

    @Override
    public Executor getExecutor() {
        if (this.executor == null) {
            this.executor = new ExecutorOperator(getContainer());
        }
        return this.executor;
    }

    @Override
    public String getExecutableHome() {
        return ""; // clients are exported on PATH
    }

    public Deployment deployContainer() {
        return deployContainer(false, null);
    }

    @Override
    public void undeployContainer() {
        kubeClient.getKubernetesClient().apps().deployments().inNamespace(namespace).resource(deployment).delete();
    }

    @Override
    public String createFile(String name, int size, String unit) {
        throw new ClaireNotImplementedException("Creating files in Pods not yet implemented!");
    }

    public Deployment deployContainer(boolean secured, List<String> secretNames) {
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
        this.deployment = deployment;
        return deployment;
    }

    public String getNamespace() {
        return namespace;
    }

    public Deployment getDeployment() {
        return deployment;
    }

    private Deployment createSystemTestsDeployment(String namespace, boolean secured,
                                  List<Volume> secretVolumes, List<VolumeMount> secretVolumeMounts) {
        Deployment systemtestClients;
        long defaultUserId = 1000L;
        if (!secured) {
            systemtestClients = new DeploymentBuilder()
                .withNewMetadata()
                    .withName(getPodName())
                .endMetadata()
                .withNewSpec()
                    .withReplicas(1)
                    .withNewSelector()
                        .addToMatchLabels("app", getPodName())
                    .endSelector()
                    .withNewTemplate()
                        .withNewMetadata()
                            .addToLabels("app", getPodName())
                        .endMetadata()
                        .withNewSpec()
                            .editOrNewSecurityContext()
                                .withRunAsNonRoot(true)
//                                .withNewSeccompProfile()
//                                    .withType("RuntimeDefault") // localhost
//                                .endSeccompProfile()
                            .endSecurityContext()
                            .addNewContainer()
                                .withName(getPodName())
                                .withImage(getContainerImageName())
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
            systemtestClients = new DeploymentBuilder()
            .withNewMetadata()
                .withName(getPodName())
            .endMetadata()
            .withNewSpec()
                .withReplicas(1)
                .withNewSelector()
                    .addToMatchLabels("app", getPodName())
                .endSelector()
                .withNewTemplate()
                    .withNewMetadata()
                        .addToLabels("app", getPodName())
                    .endMetadata()
                    .withNewSpec()
                        .editOrNewSecurityContext()
                            .withRunAsNonRoot(true)
//                            .withNewSeccompProfile()
//                                .withType("RuntimeDefault") // localhost
//                            .endSeccompProfile()
                        .endSecurityContext()
                        .withVolumes(secretVolumes)
                        .addNewContainer()
                            .withName(getPodName())
                            .withImage(getContainerImageName())
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
        if (kubeClient.isKubernetesPlatform()) {
            // add userId
            systemtestClients.getSpec().getTemplate().getSpec().getSecurityContext().setRunAsUser(defaultUserId);
        }
        return systemtestClients;
    }

}
