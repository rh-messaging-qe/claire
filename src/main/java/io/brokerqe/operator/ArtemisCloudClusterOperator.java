/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;
import io.brokerqe.KubeClient;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ArtemisCloudClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperator.class);

    private final String namespace;
    private final boolean isNamespaced;
    private final boolean isOlmInstallation;

    private List<Path> filesToDeploy;

    private final KubeClient kubeClient;

    private final String operatorName;

    public static final List<String> ZAP_LOG_LEVELS = List.of("debug", "info", "error");

    static final List<Path> DEFAULT_OPERATOR_INSTALL_CRD_FILES = Arrays.asList(
            ArtemisFileProvider.getArtemisCrdFile(),
            ArtemisFileProvider.getSecurityCrdFile(),
            ArtemisFileProvider.getAddressCrdFile(),
            ArtemisFileProvider.getScaledownCrdFile()
    );

    static final List<Path> DEFAULT_OPERATOR_INSTALL_FILES = Arrays.asList(
            ArtemisFileProvider.getServiceAccountInstallFile(),
            ArtemisFileProvider.getElectionRoleInstallFile(),
            ArtemisFileProvider.getElectionRoleBindingInstallFile(),
            ArtemisFileProvider.getOperatorConfigInstallFile(),
            ArtemisFileProvider.getOperatorInstallFile()
    );

    // Used if updated DEFAULT_OPERATOR_INSTALL_FILES
    private List<Path> operatorInstallFiles;
    private Path operatorUpdatedFile;
    private Path clusterRoleBindingUpdatedFile;

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
        this.operatorName = TestUtils.getOperatorControllerManagerName(ArtemisFileProvider.getOperatorInstallFile());
        if (isNamespaced) {
            this.filesToDeploy = new ArrayList<>(getNamespacedOperatorInstallFiles());
        } else {
            this.filesToDeploy = new ArrayList<>(getClusteredOperatorInstallFiles());
        }
        this.kubeClient = ResourceManager.getKubeClient().inNamespace(this.namespace);
    }

    public static void deployOperatorCRDs() {
        DEFAULT_OPERATOR_INSTALL_CRD_FILES.forEach(fileName -> {
            try {
                LOGGER.debug("[Operator] Deploying CRD file {}", fileName);
                ResourceManager.getKubeClient().getKubernetesClient().load(new FileInputStream(fileName.toFile())).createOrReplace();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        LOGGER.info("[Operator] Deployed Cluster operator CRDs");
    }

    public void deployOperator(boolean waitForDeployment) {
        LOGGER.info("Deploying Artemis Cluster Operator in namespace {}", namespace);
//        List<HasMetadata> deployedFilesResults = new ArrayList<>();
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Deploying file {}", namespace, fileName);
                kubeClient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).inNamespace(namespace).createOrReplace();
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
            Path operatorFile = getArtemisOperatorFile();
            // Replace operator.yaml file to use custom updated file
            // Update operator file with watch-namespaces
            LOGGER.info("Updating {} with watched namespaces {}", operatorFile, watchedNamespaces);
            Path updatedClusterOperatorFileName = TestUtils.updateOperatorFileWatchNamespaces(operatorFile, watchedNamespaces);
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
            Path clusterRoleBindingFile = getArtemisClusterRoleBindingFile();
            // Update namespace in cluster_role_binding.yaml file to use custom updated file
            LOGGER.info("Updating {} to use namespaces {}", clusterRoleBindingFile, namespace);
            Path updatedClusterRoleBindingFile = TestUtils.updateClusterRoleBindingFileNamespace(clusterRoleBindingFile, namespace);
            // Replace CRB file by newly generated in filesToDeployList
            filesToDeploy.remove(clusterRoleBindingFile);
            filesToDeploy.add(updatedClusterRoleBindingFile);
            setArtemisClusterRoleBindingFile(updatedClusterRoleBindingFile);

        } else {
            LOGGER.error("[{}] Namespaced operator does not use ClusterRoleBinding!", namespace);
            throw new RuntimeException("Incorrect ClusterOperator operation!");
        }
    }

    public void waitForCoDeployment() {
        // operator pod/deployment name activemq-artemis-controller-manager vs amq-broker-controller-manager
        kubeClient.getKubernetesClient().resource(kubeClient.getDeployment(namespace, operatorName)).waitUntilReady(3, TimeUnit.MINUTES);
    }

    public void waitForCoUndeployment() {
        Deployment amqCoDeployment = kubeClient.getDeployment(namespace, operatorName);
//        kubeClient.getKubernetesClient().resource(amqCoDeployment).waitUntilCondition(removed, 3, TimeUnit.MINUTES);
        TestUtils.waitFor("ClusterOperator to stop", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return amqCoDeployment == null && kubeClient.listPodsByPrefixInName(namespace, operatorName).size() == 0;
        });
    }

    public void undeployOperator(boolean waitForUndeployment) {
//        getUsedOperatorInstallFiles().forEach(fileName -> {
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Undeploying file {}", namespace, fileName);
                List<StatusDetails> result = kubeClient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).inNamespace(this.namespace).delete();
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

    public static void undeployOperatorCRDs(boolean waitForUndeployment) {
        DEFAULT_OPERATOR_INSTALL_CRD_FILES.forEach(fileName -> {
            try {
                LOGGER.debug("[Operator] Undeploying CRD file {}", fileName);
                List<StatusDetails> result = ResourceManager.getKubeClient().getKubernetesClient().load(new FileInputStream(fileName.toFile())).delete();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        if (waitForUndeployment) {
            // todo
            LOGGER.warn("!!wait for undeployment not implemented yet!!");
        }
        LOGGER.info("[Operator] Undeployed Cluster operator CRDs");
    }

    public String getNamespace() {
        return namespace;
    }


    protected List<Path> getClusteredOperatorInstallFiles() {
        List<Path> temp = new ArrayList<>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(ArtemisFileProvider.getClusterRoleInstallFile());
        temp.add(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        return temp;
    }

    protected List<Path> getNamespacedOperatorInstallFiles() {
        List<Path> temp = new ArrayList<>(DEFAULT_OPERATOR_INSTALL_FILES);
        temp.add(ArtemisFileProvider.getNamespaceRoleInstallFile());
        temp.add(ArtemisFileProvider.getNamespaceRoleBindingInstallFile());
        return temp;
    }

    protected List<Path> getUsedOperatorInstallFiles() {
        return operatorInstallFiles;
    }

    public Path getArtemisOperatorFile() {
        return Objects.requireNonNullElse(this.operatorUpdatedFile, ArtemisFileProvider.getOperatorInstallFile());
    }

    public void setArtemisOperatorFile(Path operatorFile) {
        if (operatorInstallFiles == null) {
            operatorInstallFiles = new ArrayList<>(List.copyOf(DEFAULT_OPERATOR_INSTALL_FILES));
        }
        operatorInstallFiles.remove(ArtemisFileProvider.getOperatorInstallFile());
        operatorInstallFiles.add(operatorFile);
        this.operatorUpdatedFile = operatorFile;
    }

    public Path getArtemisClusterRoleBindingFile() {
        return Objects.requireNonNullElse(this.clusterRoleBindingUpdatedFile, ArtemisFileProvider.getClusterRoleBindingInstallFile());
    }

    public void setArtemisClusterRoleBindingFile(Path clusterRoleBindingFile) {
        if (operatorInstallFiles == null) {
            operatorInstallFiles = new ArrayList<>(List.copyOf(DEFAULT_OPERATOR_INSTALL_FILES));
        }
        operatorInstallFiles.remove(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        operatorInstallFiles.add(clusterRoleBindingFile);
        this.clusterRoleBindingUpdatedFile = clusterRoleBindingFile;
    }

    public String getOperatorName() {
        return operatorName;
    }

}
