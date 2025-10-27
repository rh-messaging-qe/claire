/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.operator;

import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentOperator;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public abstract class ArtemisCloudClusterOperator {

    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperator.class);
    public static final List<String> ZAP_LOG_LEVELS = List.of("debug", "info", "error");
    public static final String ZAP_LOG_LEVEL_OPTION = "--zap-log-level";
    public static final String LEASE_DURATION_OPTION = "--lease-duration";
    public static final String RENEW_DEADLINE_OPTION = "--renew-deadline";
    public static final String RETRY_PERIOD_OPTION = "--retry-period";
    protected final String deploymentNamespace;
    protected final boolean isNamespaced;
    protected final EnvironmentOperator environmentOperator;
    protected final List<String> watchedNamespaces;
    protected final KubeClient kubeClient;
    protected String operatorName;
    public String amqBrokerOperatorName = "amq-broker-operator";
    private final String operatorOldNameSuffix = "-operator";
    private final String operatorNewNameSuffix = "-controller-manager";

    public ArtemisCloudClusterOperator(String namespace) {
        this(namespace, true, null);
    }

    public ArtemisCloudClusterOperator(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces) {
        this.deploymentNamespace = deploymentNamespace;
        this.isNamespaced = isNamespaced;
        this.environmentOperator = ResourceManager.getEnvironment();
        this.watchedNamespaces = watchedNamespaces;

        if (environmentOperator.isOlmInstallation()) {
            // try amq-broker-operator or new name
            this.operatorName = operatorNewNameSuffix;
        } else {
            this.operatorName = getOperatorControllerManagerName(ArtemisFileProvider.getOperatorInstallFile());
        }
        this.kubeClient = ResourceManager.getKubeClient().inNamespace(this.deploymentNamespace);
    }

    abstract public void deployOperator(boolean waitForDeployment);

    abstract public void undeployOperator(boolean waitForUndeployment);

    abstract public void setOperatorLogLevel(String logLevel);

    abstract public void setOperatorLeaseDuration(int durationInSeconds, boolean waitForReadiness);

    abstract public void setOperatorRenewDeadlineDuration(int durationInSeconds, boolean waitReadiness);

    abstract public void setOperatorRetryPeriodDuration(int durationInSeconds, boolean waitReadiness);

    public void refreshDeploymentData() {
        waitForCoDeployment(true);
    }

    public void waitForCoDeployment() {
        waitForCoDeployment(false);
    }

    public void waitForCoDeployment(boolean isRefreshOnly) {
        // operator pod/deployment name activemq-artemis-controller-manager vs amq-broker-controller-manager
        TestUtils.waitFor("deployment to be active", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES,
                () -> kubeClient.getDeployment(deploymentNamespace, getOperatorNewName()) != null ||
                        kubeClient.getDeployment(deploymentNamespace, getOperatorOldName()) != null);

        Deployment deployment = kubeClient.getDeployment(deploymentNamespace, getOperatorNewName());
        if (deployment == null) {
            deployment = kubeClient.getDeployment(deploymentNamespace, getOperatorOldName());
            this.operatorName = getOperatorOldName();
        } else {
            this.operatorName = getOperatorNewName();
        }
        if (!isRefreshOnly) {
            kubeClient.getKubernetesClient().resource(deployment).waitUntilReady(3, TimeUnit.MINUTES);
        }
    }

    public void waitForCoUndeployment() {
        Deployment amqCoDeployment = kubeClient.getDeployment(deploymentNamespace, operatorName);
//        kubeClient.getKubernetesClient().resource(amqCoDeployment).waitUntilCondition(removed, 3, TimeUnit.MINUTES);
        TestUtils.waitFor("ClusterOperator to stop", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return amqCoDeployment == null && kubeClient.listPodsByPrefixName(deploymentNamespace, operatorName).size() == 0;
        });
    }

    public String getDeploymentNamespace() {
        return deploymentNamespace;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getOperatorOldName() {
        return environmentOperator.getArtemisOperatorName() + operatorOldNameSuffix;
    }

    public String getOperatorNewName() {
        return environmentOperator.getArtemisOperatorName() + operatorNewNameSuffix;
    }

    public static String getOperatorControllerManagerName(Path yamlFile) {
        Deployment operatorCODeployment = TestUtils.configFromYaml(yamlFile.toFile(), Deployment.class);
        return operatorCODeployment.getMetadata().getName();
    }

    public String getOperatorOLMVersion(boolean returnMMM) {
        if (environmentOperator.isOlmInstallation()) {
//            String channel = ((ArtemisCloudClusterOperatorOlm) this).getOlmChannel();
            ClusterServiceVersion csv = kubeClient.getClusterServiceVersion(deploymentNamespace, amqBrokerOperatorName);
            String version = csv.getSpec().getVersion();
            if (returnMMM) {
                return TestUtils.parseVersionMMM(version);
            } else {
                return version;
            }
        } else {
            throw new ClaireRuntimeException("Operator is not installed using OLM!");
        }
    }

    public PackageManifest getRelatedPackageManifestOlm(boolean filterByCustomCatalogSource) {
        if (environmentOperator.isOlmInstallation()) {
            List<PackageManifest> pms = ResourceManager.getKubeClient().getPackageManifests(ArtemisCloudClusterOperatorOlm.getAmqOperatorName());
            for (PackageManifest pm : pms) {
                LOGGER.info("PM: {} -> CS: {}", pm.getMetadata().getName(), pm.getStatus().getCatalogSource());
                if (filterByCustomCatalogSource) {
                    String catalogSourceName = ((ArtemisCloudClusterOperatorOlm) this).getBrokerCatalogSourceName();
                    if (pm.getStatus().getCatalogSource().equals(catalogSourceName)) {
                        LOGGER.info("Returning PM: {} with CS: {}", pm.getMetadata().getName(), pm.getStatus().getCatalogSource());
                        return pm;
                    }
                } else {
                    LOGGER.info("Returning first found PM: {} with CS: {}", pm.getMetadata().getName(), pm.getStatus().getCatalogSource());
                    return pm;
                }
            }
        } else {
            throw new ClaireRuntimeException("Operator is not installed using OLM!");
        }
        return null;
    }

    public boolean isOperatorReady(long maxTimeout) {
        LOGGER.debug("[{}] Waiting for readiness of operator pod {}", deploymentNamespace, operatorName);
        Predicate<Pod> readyCondition = tmpPod -> {
            if (tmpPod == null || tmpPod.getStatus() == null || tmpPod.getStatus().getConditions() == null ||
                    !"Running".equalsIgnoreCase(tmpPod.getStatus().getPhase())) {
                return false;
            }
            return tmpPod.getStatus().getConditions().stream()
                    .anyMatch(cond ->
                            ArtemisConstants.CONDITION_TYPE_READY.equalsIgnoreCase(cond.getType()) &&
                                    ArtemisConstants.CONDITION_TRUE.equalsIgnoreCase(cond.getStatus())
                    );
        };

        Resource<Pod> podResource = kubeClient.getKubernetesClient().pods()
                .inNamespace(deploymentNamespace)
                .withName(operatorName);
        podResource.waitUntilCondition(readyCondition, maxTimeout, TimeUnit.MILLISECONDS);
        return podResource.isReady();
    }

    protected void patchKubernetesPullSecretServiceAccount() {
        Map<String, String> labels = new HashMap<>();
        labels.put("control-plane", "controller-manager");
        labels.put("rht.subcomp", "broker-amq-operator");
        TestUtils.waitFor("operator pod to shows up", Constants.DURATION_5_SECONDS, Constants.DURATION_1_MINUTE,
                () -> kubeClient.getPodByLabels(deploymentNamespace, labels) != null
        );
        refreshDeploymentData();
        String pullSecretFile = ResourceManager.getEnvironment().getKubePullSecret();
        LOGGER.info("[{}][EKS] Deploy pull-secret from file {}", deploymentNamespace, pullSecretFile);
        kubeClient.createSecretFromFile(deploymentNamespace, Constants.AMQ_BROKER_QE_PULL_SECRET, pullSecretFile);

        LOGGER.info("[{}][EKS] Patch ServiceAccounts {}, {} to use pull-secret.", deploymentNamespace, "default", getOperatorName());
        kubeClient.patchServiceAccountWithPullSecret(deploymentNamespace, "default", Constants.AMQ_BROKER_QE_PULL_SECRET);
        kubeClient.patchServiceAccountWithPullSecret(deploymentNamespace, getOperatorName(), Constants.AMQ_BROKER_QE_PULL_SECRET);

        // delete any deployed operator pods
        Pod wrongPod = kubeClient.getFirstPodByPrefixName(deploymentNamespace, getOperatorName());
        LOGGER.info("[{}][EKS] Deleting pod {} deployed before SA & Pull Secret updates", deploymentNamespace, wrongPod.getMetadata().getName());
        kubeClient.deletePod(deploymentNamespace, wrongPod, true);
    }

}
