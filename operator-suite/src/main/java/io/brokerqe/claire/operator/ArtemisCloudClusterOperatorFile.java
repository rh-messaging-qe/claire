/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.operator;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
            ArtemisFileProvider.getOperatorInstallFile()
    );
    private List<Path> filesToDeploy;

    // Used if updated DEFAULT_OPERATOR_INSTALL_FILES
    private List<Path> operatorInstallFiles;
    private Path operatorUpdatedFile;
    private Path clusterRoleBindingUpdatedFile;

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
        for (KubeClient kubeclient : ResourceManager.getKubeClients()) {
            DEFAULT_OPERATOR_INSTALL_CRD_FILES.forEach(fileName -> {
                try {
                    ArtemisCloudClusterOperator.LOGGER.debug("[Operator] Deploying CRD file {}", fileName);
                    List<HasMetadata> resources = kubeclient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).createOrReplace();
                    DataStorer.dumpResourceToFile(resources);
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            });
            ArtemisCloudClusterOperator.LOGGER.info("[Operator] Deployed Cluster operator CRDs");
        }
    }

    public static void undeployOperatorCRDs(boolean waitForUndeployment) {
        for (KubeClient kubeclient : ResourceManager.getKubeClients()) {
            DEFAULT_OPERATOR_INSTALL_CRD_FILES.forEach(fileName -> {
                try {
                    LOGGER.debug("[Operator] Undeploying CRD file {}", fileName);
                    List<StatusDetails> result = kubeclient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).delete();
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
    }

    @Override
    public void deployOperator(boolean waitForDeployment) {
        LOGGER.info("[FILE] Deploying Artemis Cluster Operator in namespace {}", deploymentNamespace);
//        List<HasMetadata> deployedFilesResults = new ArrayList<>();
        filesToDeploy.forEach(fileName -> {
            try {
                LOGGER.debug("[{}] Deploying file {}", deploymentNamespace, fileName);
                List<HasMetadata> resources = kubeClient.getKubernetesClient().load(new FileInputStream(fileName.toFile())).inNamespace(deploymentNamespace).createOrReplace();
                DataStorer.dumpResourceToFile(resources);
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
            Path updatedClusterRoleBindingFile = updateClusterRoleBindingFileNamespace(clusterRoleBindingFile, namespace);
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
            Path updatedClusterOperatorFileName = updateOperatorFileWatchNamespaces(operatorFile, watchedNamespaces);
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
        List<Path> temp = new ArrayList<>(getOperatorInstallFiles());
        temp.add(ArtemisFileProvider.getClusterRoleInstallFile());
        temp.add(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        return temp;
    }

    private List<Path> getOperatorInstallFiles() {
        ArrayList<Path> files = new ArrayList<>();
        files.add(ArtemisFileProvider.getServiceAccountInstallFile());
        files.add(ArtemisFileProvider.getElectionRoleInstallFile());
        files.add(ArtemisFileProvider.getElectionRoleInstallFile());
        files.add(ArtemisFileProvider.getElectionRoleBindingInstallFile());
        files.add(ArtemisFileProvider.getOperatorInstallFile());
        return files;
    }

    protected List<Path> getNamespacedOperatorInstallFiles() {
        List<Path> temp = getOperatorInstallFiles();
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
            operatorInstallFiles = new ArrayList<>(List.copyOf(getOperatorInstallFiles()));
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
            operatorInstallFiles = new ArrayList<>(List.copyOf(getOperatorInstallFiles()));
        }
        operatorInstallFiles.remove(ArtemisFileProvider.getClusterRoleBindingInstallFile());
        operatorInstallFiles.add(clusterRoleBindingFile);
        this.clusterRoleBindingUpdatedFile = clusterRoleBindingFile;
    }

    public static void updateImagesInOperatorFile(Path operatorFile, String imageType, String imageUrl, String version) {
        List<EnvVar> envVars;
        String imageTypeVersion = null;
        Deployment operator = TestUtils.configFromYaml(operatorFile.toFile(), Deployment.class);

        if (version != null) {
            if (version.equals("main")) {
                imageTypeVersion = imageType + getLastVersionFromOperatorFile(imageType, operator);
            } else {
                imageTypeVersion = imageType + version.replace(".", "");
            }
        }

        if (imageType.equals(ArtemisConstants.OPERATOR_IMAGE_OPERATOR_PREFIX)) {
            operator.getSpec().getTemplate().getSpec().getContainers().get(0).setImage(imageUrl);
            LOGGER.info("[Operator] Updating OperatorImage -> {}", imageUrl);
        }

        if (imageType.equals(ArtemisConstants.BROKER_IMAGE_OPERATOR_PREFIX) || imageType.equals(ArtemisConstants.BROKER_INIT_IMAGE_OPERATOR_PREFIX)) {
            envVars = operator.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
            String finalImageTypeVersion = imageTypeVersion;
            Optional<EnvVar> optEnvVar = envVars.stream().filter(envVar -> envVar.getName().equals(finalImageTypeVersion)).findFirst();
            EnvVar brokerImageEV;
            if (optEnvVar.isPresent()) {
                brokerImageEV = optEnvVar.get();
            } else {
                brokerImageEV = envVars.stream()
                        .filter(envVar -> envVar.getName().contains(imageType))
                        .sorted(Comparator.comparing(EnvVar::getName).reversed())
                        .findFirst()
                        .orElse(null);
            }
            brokerImageEV.setValue(imageUrl);
            LOGGER.info("[Operator] Updating {} -> {}", finalImageTypeVersion, imageUrl);
        }

        TestUtils.configToYaml(operatorFile.toFile(), operator);
    }

    public static Path updateClusterRoleBindingFileNamespace(Path yamlFile, String namespace) {
        String newCRBFileName = "cluster_role_binding_" + TestUtils.getRandomString(3) + ".yaml";
        Path copyPath = Paths.get(yamlFile.toAbsolutePath().toString().replace("cluster_role_binding.yaml", newCRBFileName));
        ClusterRoleBinding updatedCRB = TestUtils.configFromYaml(yamlFile.toFile(), ClusterRoleBinding.class);
        updatedCRB.getSubjects().get(0).setNamespace(namespace);
        TestUtils.configToYaml(copyPath.toFile(), updatedCRB);
        return copyPath;
    }

    public static Path updateOperatorFileWatchNamespaces(Path yamlFile, List<String> watchedNamespaces) {
        String newCOFileName = "operator_cw_" + TestUtils.getRandomString(3) + ".yaml";
        Path copyPath = Paths.get(yamlFile.toAbsolutePath().toString().replace("operator.yaml", newCOFileName));

        // Load Deployment from yaml file
        Deployment updatedCO = TestUtils.configFromYaml(yamlFile.toFile(), Deployment.class);

        // Get and update WATCH_NAMESPACE value
        updatedCO.getSpec().getAdditionalProperties().get("containers");
        List<EnvVar> envVars = updatedCO.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        EnvVar watchNamespaceEV = envVars.stream().filter(envVar -> envVar.getName().equals("WATCH_NAMESPACE")).findFirst().get();
        watchNamespaceEV.setValue(String.join(",", watchedNamespaces));
        watchNamespaceEV.setValueFrom(null);
        updatedCO.getSpec().getTemplate().getSpec().getContainers().get(0).setEnv(envVars);

        // Write updated Deployment into file
        // mapper.writeValue(copyPath.toFile(), updatedCO);
        TestUtils.configToYaml(copyPath.toFile(), updatedCO);
        return copyPath;
    }

    public static String getOperatorControllerManagerName(Path yamlFile) {
        Deployment operatorCODeployment = TestUtils.configFromYaml(yamlFile.toFile(), Deployment.class);
        return operatorCODeployment.getMetadata().getName();
    }

    @Override
    public void setOperatorLogLevel(String logLevel) {
        if (ArtemisCloudClusterOperator.ZAP_LOG_LEVELS.contains(logLevel)) {
            Deployment deployment = kubeClient.getDeployment(getDeploymentNamespace(), getOperatorName());
            Pod podOld = kubeClient.getFirstPodByPrefixName(getDeploymentNamespace(), getOperatorName());
            List<String> args = deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getArgs();
            List<String> argsUpdated = new ArrayList<>();

            for (String arg : args) {
                if (arg.contains(ZAP_LOG_LEVEL_OPTION)) {
                    argsUpdated.add(ZAP_LOG_LEVEL_OPTION + "=" + logLevel.toLowerCase(Locale.ROOT));
                } else {
                    argsUpdated.add(arg);
                }
            }

            if (!args.equals(argsUpdated)) {
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).setArgs(argsUpdated);
                kubeClient.setDeployment(getDeploymentNamespace(), deployment);
                kubeClient.waitForPodReload(getDeploymentNamespace(), podOld, getOperatorName());
                LOGGER.info("[{}] Changed operator {} log level to {}", getDeploymentNamespace(), getOperatorName(), logLevel);
            } else {
                LOGGER.debug("[{}] Reload is not needed, zap-log-level is {} as expected", getDeploymentNamespace(), logLevel);
            }
        } else {
            LOGGER.error("[{}] Unable to set provided log level to operator {}", getDeploymentNamespace(), getOperatorName());
        }
    }

    public static String getLastVersionFromOperatorFile(String imageType, Deployment operator) {
        List<EnvVar> envVars1 = operator.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv();
        List<Integer> versions = new ArrayList<>();

        for (EnvVar var : envVars1) {
            if (var.getName().startsWith(imageType)) {
                versions.add(Integer.valueOf(var.getName().substring(imageType.length())));
            }
        }
        Collections.sort(versions);
        LOGGER.trace("[Operator] Discovered latest version: {}{}", imageType, versions.get(versions.size() - 1));
        return String.valueOf(versions.get(versions.size() - 1));
    }

}
