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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// make this class abstract (ArtemisClusterOperator, AMQClusterOperator) --> ActiveMQArtemisClusterOperator
public abstract class ActiveMQArtemisClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ActiveMQArtemisClusterOperator.class);

    private final String namespace;
    private final boolean isNamespaced;
    private final boolean isOlmInstallation;

    private List<String> filesToDeploy;

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

    public void deployOperator(boolean waitForDeployment) {
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

    protected void watchAllNamespaces() {
        watchNamespaces(List.of("*"));
    }

    public void watchNamespaces(List<String> watchedNamespaces) {
        if (!isNamespaced) {
            String operatorFile = getArtemisOperatorFile();
            // Replace 110_operator.yaml file to use custom updated file
            // Update operator file with watch-namespaces
            LOGGER.info("Updating {} with watched namespaces {}", operatorFile, watchedNamespaces);
            String updatedClusterOperatorFileName = TestUtils.updateOperatorFileWatchNamespaces(Paths.get(operatorFile), watchedNamespaces);
            // Replace operatorFile by newly generated in filesToDeployList
            filesToDeploy.remove(operatorFile);
            filesToDeploy.add(updatedClusterOperatorFileName);
            setArtemisOperatorFile(updatedClusterOperatorFileName);

        } else {
            LOGGER.error("[{}] Namespaced operator can't watch other namespaces {}!", namespace, watchedNamespaces);
            throw new RuntimeException("Incorrect ClusterOperator operation!");
        }
    }

    public void updateClusterRoleBinding(String namespace) {
        if (!isNamespaced) {
            String clusterRoleBindingFile = getArtemisClusterRoleBindingFile();
            // Update namespace in 070_cluster_role_binding.yaml file to use custom updated file
            LOGGER.info("Updating {} to use namespaces {}", clusterRoleBindingFile, namespace);
            String updatedClusterRoleBindingFile = TestUtils.updateClusterRoleBindingFileNamespace(Paths.get(clusterRoleBindingFile), namespace);
            // Replace CRB file by newly generated in filesToDeployList
            filesToDeploy.remove(clusterRoleBindingFile);
            filesToDeploy.add(updatedClusterRoleBindingFile);
            setArtemisClusterRoleBindingFile(updatedClusterRoleBindingFile);

        } else {
            LOGGER.error("[{}] Namespaced operator does not use ClusterRoleBinding!", namespace);
            throw new RuntimeException("Incorrect ClusterOperator operation!");
        }
    }

    private void waitForCoDeployment() {
        // operator pod/deployment name activemq-artemis-controller-manager vs amq-broker-controller-manager
        TestUtils.waitFor("ClusterOperator to start in " + namespace, Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return kubeClient.getDeployment(namespace, operatorName).getStatus().getReadyReplicas().equals(kubeClient.getDeployment(namespace, operatorName).getSpec().getReplicas())
                && kubeClient.getFirstPodByPrefixName(namespace, operatorName) != null
                && kubeClient.getFirstPodByPrefixName(namespace, operatorName).getStatus().getPhase().equals("Running");
        });
    }

    private void waitForCoUndeployment() {
        Deployment amqCoDeployment = kubeClient.getDeployment(namespace, operatorName);
        TestUtils.waitFor("ClusterOperator to stop", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return amqCoDeployment == null && kubeClient.listPodsByPrefixInName(namespace, operatorName).size() == 0;
        });
    }

    public void undeployOperator(boolean waitForUndeployment) {
//        getUsedOperatorInstallFiles().forEach(fileName -> {
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
        LOGGER.info("[{}] Undeployed Cluster operator {}", namespace, operatorName);
        if (!isNamespaced) {
            TestUtils.deleteFile(getArtemisOperatorFile());
            TestUtils.deleteFile(getArtemisClusterRoleBindingFile());
            LOGGER.info("[{}] Removed cluster-wide {} and {}", namespace, getArtemisOperatorFile(), getArtemisClusterRoleBindingFile());
        }
    }

    public String getNamespace() {
        return namespace;
    }


    protected abstract List<String> getClusteredOperatorInstallFiles();
    protected abstract List<String> getNamespacedOperatorInstallFiles();
    protected abstract List<String> getUsedOperatorInstallFiles();

    public abstract void setArtemisOperatorFile(String operatorFile);

    public abstract String getArtemisOperatorFile();
    public abstract String getArtemisClusterRoleBindingFile();
    public abstract void setArtemisClusterRoleBindingFile(String clusterRoleBindingFile);

    // TODO Temporary solution
    public abstract String getArtemisSingleExamplePath();

    public abstract String getArtemisAddressQueueExamplePath();

}
