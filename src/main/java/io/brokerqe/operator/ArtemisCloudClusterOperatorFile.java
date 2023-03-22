/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.StatusDetails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ArtemisCloudClusterOperatorFile extends ArtemisCloudClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperatorFile.class);

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
    private List<Path> filesToDeploy;

    // Used if updated DEFAULT_OPERATOR_INSTALL_FILES
    private List<Path> operatorInstallFiles;
    private Path operatorUpdatedFile;
    private Path clusterRoleBindingUpdatedFile;


//    public FileInstallation(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces) {
//        this(deploymentNamespace, isNamespaced, watchedNamespaces);
//    }

    public ArtemisCloudClusterOperatorFile(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces) {
        super(deploymentNamespace, isNamespaced, watchedNamespaces);

        if (isNamespaced) {
            this.filesToDeploy = new ArrayList<>(getNamespacedOperatorInstallFiles());
        } else {
            this.filesToDeploy = new ArrayList<>(getClusteredOperatorInstallFiles());
            watchNamespaces(watchedNamespaces);
            updateClusterRoleBinding(deploymentNamespace);
        }
    }


    public static void deployOperatorCRDs() {
        DEFAULT_OPERATOR_INSTALL_CRD_FILES.forEach(fileName -> {
            try {
                ArtemisCloudClusterOperator.LOGGER.debug("[Operator] Deploying CRD file {}", fileName);
                ResourceManager.getKubeClient().getKubernetesClient().load(new FileInputStream(fileName.toFile())).createOrReplace();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        ArtemisCloudClusterOperator.LOGGER.info("[Operator] Deployed Cluster operator CRDs");
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

    @Override
    public void deployOperator(boolean waitForDeployment) {
        LOGGER.info("[FILE] Deploying Artemis Cluster Operator in namespace {}", deploymentNamespace);
//        List<HasMetadata> deployedFilesResults = new ArrayList<>();
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Deploying file {}", deploymentNamespace, fileName);
                kubeClient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).inNamespace(deploymentNamespace).createOrReplace();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        if (waitForDeployment) {
            waitForCoDeployment();
        }
        LOGGER.info("[{}] Cluster operator {} successfully deployed!", deploymentNamespace, operatorName);
    }


    @Override
    public void undeployOperator(boolean waitForUndeployment) {
        getUsedOperatorInstallFilesReversed().forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Undeploying file {}", deploymentNamespace, fileName);
                List<StatusDetails> result = kubeClient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).inNamespace(this.deploymentNamespace).delete();
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        if (waitForUndeployment) {
            waitForCoUndeployment();
        }
        LOGGER.info("[{}] Undeployed Cluster operator {}", deploymentNamespace, operatorName);
        if (!isNamespaced) {
            TestUtils.deleteFile(getArtemisOperatorFile());
            TestUtils.deleteFile(getArtemisClusterRoleBindingFile());
            LOGGER.info("[{}] Removed cluster-wide {} and {}", deploymentNamespace, getArtemisOperatorFile(), getArtemisClusterRoleBindingFile());
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
            LOGGER.error("[{}] Namespaced operator can't watch other namespaces {}!", deploymentNamespace, watchedNamespaces);
            throw new RuntimeException("Incorrect ClusterOperator operation!");
        }
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

    protected List<Path> getUsedOperatorInstallFilesReversed() {
        List<Path> reversedFilesToDeploy = new ArrayList<>(filesToDeploy);
        Collections.copy(reversedFilesToDeploy, filesToDeploy);
        Collections.reverse(reversedFilesToDeploy);
        return reversedFilesToDeploy;
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

}
