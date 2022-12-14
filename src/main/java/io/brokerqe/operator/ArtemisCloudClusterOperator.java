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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ArtemisCloudClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperator.class);

    private final String namespace;
    private final boolean isNamespaced;
    private final boolean isOlmInstallation;

    private List<String> filesToDeploy;

    private final KubeClient kubeClient;

    private final String operatorName;

    static final List<String> DEFAULT_OPERATOR_INSTALL_FILES = Arrays.asList(
            ArtemisFileProvider.getArtemisCrdFile(),
            ArtemisFileProvider.getSecurityCrdFile(),
            ArtemisFileProvider.getAddressCrdFile(),
            ArtemisFileProvider.getScaledownCrdFile(),
            ArtemisFileProvider.getServiceAccountInstallFile(),
            ArtemisFileProvider.getElectionRoleInstallFile(),
            ArtemisFileProvider.getElectionRoleBindingInstallFile(),
            ArtemisFileProvider.getOperatorConfigInstallFile(),
            ArtemisFileProvider.getOperatorInstallFile()
    );

    // Used if updated DEFAULT_OPERATOR_INSTALL_FILES
    private List<String> operatorInstallFiles;
    private String operatorUpdatedFile;
    private String clusterRoleBindingUpdatedFile;

    public ArtemisCloudClusterOperator(String namespace) {
        this(namespace, false, true);
    }

    public ArtemisCloudClusterOperator(String namespace, boolean isNamespaced) {
        this(namespace, false, isNamespaced);
    }

    public ArtemisCloudClusterOperator(String namespace, boolean isOlmInstallation, boolean isNamespaced) {
        this.namespace = namespace;
        this.isOlmInstallation = isOlmInstallation;
        this.isNamespaced = isNamespaced;
        this.operatorName = TestUtils.getOperatorControllerManagerName(Paths.get(ArtemisFileProvider.getOperatorInstallFile()));
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


    protected List<String> getClusteredOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(ArtemisFileProvider.getClusterRoleInstallFile());
        temp.add(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        return temp;
    }

    protected List<String> getNamespacedOperatorInstallFiles() {
        List<String> temp = new ArrayList<String>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(ArtemisFileProvider.getNamespaceRoleInstallFile());
        temp.add(ArtemisFileProvider.getNamespaceRoleBindingInstallFile());
        return temp;
    }

    protected List<String> getUsedOperatorInstallFiles() {
        return operatorInstallFiles;
    }

    public String getArtemisOperatorFile() {
        return Objects.requireNonNullElse(this.operatorUpdatedFile, ArtemisFileProvider.getOperatorInstallFile());
    }

    public void setArtemisOperatorFile(String operatorFile) {
        if (operatorInstallFiles == null) {
            operatorInstallFiles = new ArrayList<>(List.copyOf(DEFAULT_OPERATOR_INSTALL_FILES));
        }
        operatorInstallFiles.remove(ArtemisFileProvider.getOperatorInstallFile());
        operatorInstallFiles.add(operatorFile);
        this.operatorUpdatedFile = operatorFile;
    }

    public String getArtemisClusterRoleBindingFile() {
        return Objects.requireNonNullElse(this.clusterRoleBindingUpdatedFile, ArtemisFileProvider.getClusterRoleBindingInstallFile());
    }

    public void setArtemisClusterRoleBindingFile(String clusterRoleBindingFile) {
        if (operatorInstallFiles == null) {
            operatorInstallFiles = new ArrayList<>(List.copyOf(DEFAULT_OPERATOR_INSTALL_FILES));
        }
        operatorInstallFiles.remove(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        operatorInstallFiles.add(clusterRoleBindingFile);
        this.clusterRoleBindingUpdatedFile = clusterRoleBindingFile;
    }

}
