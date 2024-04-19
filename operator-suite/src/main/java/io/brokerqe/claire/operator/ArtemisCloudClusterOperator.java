/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.operator;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentOperator;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    public void waitForCoDeployment() {
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
        kubeClient.getKubernetesClient().resource(deployment).waitUntilReady(3, TimeUnit.MINUTES);
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

    abstract public void setOperatorLogLevel(String logLevel);

    abstract public void setOperatorLeaseDuration(int durationInSeconds);

    abstract public void setOperatorRenewDeadlineDuration(int durationInSeconds);

    abstract public void setOperatorRetryPeriodDuration(int durationInSeconds);

}
