/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

// make this class abstract (ArtemisClusterOperator, AMQClusterOperator) --> ActiveMQArtemisClusterOperator
public abstract class ActiveMQArtemisClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ActiveMQArtemisClusterOperator.class);

    private final String namespace;
    private final boolean isNamespaced;
    private final boolean isOlmInstallation;

    private final List<String> filesToDeploy;

    private final KubeClient kubeClient;

    // TODO: abstract -> default downstream for now
    private final String operatorName;

    public ActiveMQArtemisClusterOperator(String namespace, String operatorName) {
        this(namespace, false, true, operatorName);
    }
    public ActiveMQArtemisClusterOperator(String namespace, boolean isOlmInstallation, boolean isNamespaced, String operatorName) {
        this.namespace = namespace;
        this.isOlmInstallation = isOlmInstallation;
        this.isNamespaced = isNamespaced;
        this.operatorName = operatorName;
        if (isNamespaced) {
            this.filesToDeploy = new ArrayList<>(getNamespacedOperatorInstallFiles());
        } else {
            this.filesToDeploy = new ArrayList<>(getClusteredOperatorInstallFiles());
        }
        this.kubeClient = new KubeClient(this.namespace);

    }

    protected abstract List<String> getClusteredOperatorInstallFiles();
    protected abstract List<String> getNamespacedOperatorInstallFiles();

    public void deployOperator(boolean waitForDeployment) {
        // TODO where to deploy it properly for cluster-wide?
        LOGGER.info("Deploying Artemis Cluster Operator in namespace {}", namespace);
//        List<HasMetadata> deployedFilesResults = new ArrayList<>();
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Deploying file {}", namespace, fileName);
                kubeClient.getKubernetesClient().load(new FileInputStream(fileName)).inNamespace(namespace).createOrReplace();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        if (waitForDeployment) {
            waitForCoDeployment();
        }
        LOGGER.info("[{}] Cluster operator {} successfully deployed!", namespace, operatorName);
    }

    private void waitForCoDeployment() {
        // operator pod/deployment name activemq-artemis-controller-manager vs amq-broker-controller-manager
        TestUtils.waitFor("Wait for ClusterOperator to start", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return kubeClient.getDeployment(namespace, operatorName).getStatus().getReadyReplicas().equals(kubeClient.getDeployment(namespace, operatorName).getSpec().getReplicas())
                && kubeClient.getFirstPodByPrefixName(namespace, operatorName) != null
                && kubeClient.getFirstPodByPrefixName(namespace, operatorName).getStatus().getPhase().equals("Running");
        });
    }

    private void waitForCoUndeployment() {
        Deployment amqCoDeployment = kubeClient.getDeployment(namespace, operatorName);
        TestUtils.waitFor("Wait for ClusterOperator to start", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return amqCoDeployment == null && kubeClient.listPodsByPrefixInName(namespace, operatorName).size() == 0;
        });
    }

    public void undeployOperator(boolean waitForUndeployment) {
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Undeploying file {}", namespace, fileName);
                List<StatusDetails> result = kubeClient.getKubernetesClient().load(new FileInputStream(fileName)).inNamespace(this.namespace).delete();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        if (waitForUndeployment) {
            waitForCoUndeployment();
        }
        LOGGER.info("[{}] Undeployed Cluster operator {}!", namespace, operatorName);
    }

    public String getNamespace() {
        return namespace;
    }

    // TODO Temporary solution
    public abstract String getArtemisSingleExamplePath();

    public abstract String getArtemisAddressQueueExamplePath();

}
