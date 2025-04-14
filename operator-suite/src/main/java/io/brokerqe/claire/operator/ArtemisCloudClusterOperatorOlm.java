/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.operator;

import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.exception.WaitException;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.PackageChannel;
import io.fabric8.openshift.api.model.operatorhub.packages.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionConfig;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.module.ModuleDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArtemisCloudClusterOperatorOlm extends ArtemisCloudClusterOperator {
    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperatorOlm.class);
    private String olmChannel;
    private String indexImageBundle;
    private String brokerCatalogSourceName = "broker-source-" + TestUtils.getRandomString(2);
    private String subscriptionName = getAmqOperatorName() + "-" + TestUtils.getRandomString(2);
    private String sourceNamespace;
    private List<HasMetadata> olmResources = new ArrayList<>();

    public ArtemisCloudClusterOperatorOlm(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces, boolean isOlmLts) {
        // use this as default released installation using OLM
        super(deploymentNamespace, isNamespaced, watchedNamespaces);
        this.brokerCatalogSourceName = "redhat-operators";
        this.sourceNamespace = "openshift-marketplace";
        getPackageManifestChannel(isOlmLts);
    }

    public ArtemisCloudClusterOperatorOlm(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces, String indexImageBundle, String olmChannel) {
        super(deploymentNamespace, isNamespaced, watchedNamespaces);
        this.indexImageBundle = indexImageBundle;
        this.olmChannel = olmChannel;
    }

    public static String getAmqOperatorName() {
        String rhelVersion;
        if (ResourceManager.getEnvironment().getArtemisTestVersion().getVersionNumber() >= ArtemisVersion.VERSION_2_40.getVersionNumber()) {
            rhelVersion = "9";
        } else {
            rhelVersion = "8";
        }
        return "amq-broker-rhel" + rhelVersion;
    }

    public String getOlmChannel() {
        return olmChannel;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    public String getBrokerCatalogSourceName() {
        return brokerCatalogSourceName;
    }

    private void getPackageManifestChannel(boolean isLts) {
        String olmCSV;
        PackageChannel nonLtsChannel;
        PackageChannel ltsChannel;

        PackageManifest amqBrokerPM = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub()
                .packageManifests().inNamespace(sourceNamespace).withName(getAmqOperatorName()).get();
        if (!amqBrokerPM.getStatus().getCatalogSource().equals(brokerCatalogSourceName)) {
            LOGGER.error("[{}] Found unexpected CatalogSource for `amq-broker-rhel{8|9}` {}!", deploymentNamespace, amqBrokerPM.getStatus().getCatalogSource());
            throw new ClaireRuntimeException("Discovered unexpected CatalogSource " + amqBrokerPM.getStatus().getCatalogSource() + "!");
        }

        List<String> channels = amqBrokerPM.getStatus().getChannels().stream().map(PackageChannel::getName).toList();
        List<String> orderedChannels = channels.stream().map(ModuleDescriptor.Version::parse).sorted().map(ModuleDescriptor.Version::toString).toList();
        List<String> reverseOrderedChannels = new ArrayList<>(orderedChannels);
        Collections.reverse(reverseOrderedChannels);

        nonLtsChannel = amqBrokerPM.getStatus().getChannels().stream()
                .filter(e -> e.getName().equals(reverseOrderedChannels.get(0)))
                .findFirst()
                .orElseThrow();
        ltsChannel = amqBrokerPM.getStatus().getChannels().stream()
                .filter(e -> e.getName().equals(reverseOrderedChannels.get(1)))
                .findFirst()
                .orElseThrow();

        if (isLts) {
            olmCSV = ltsChannel.getCurrentCSV();
            this.olmChannel = ltsChannel.getName();
        } else {
            olmCSV = nonLtsChannel.getCurrentCSV();
            this.olmChannel = nonLtsChannel.getName();
        }
        LOGGER.info("[{}] Going to install {} from channel: {}", deploymentNamespace, olmCSV, olmChannel);
    }


    public void deployCatalogSource(String indexImageBundle) {
        String catalogSource = String.format("""
            apiVersion: operators.coreos.com/v1alpha1
            kind: CatalogSource
            metadata:
              name: %s
              namespace: openshift-marketplace
            spec:
              sourceType: grpc
              image: %s
            """, brokerCatalogSourceName, indexImageBundle);

        LOGGER.info("[OLM] Creating CatalogSource");
        deployOlmResource(catalogSource);
    }

    public void deploySubscription(String olmChannel) {
        if (olmChannel == null) {
            olmChannel = this.olmChannel;
        }

        String subscriptionString = String.format("""
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
              name: %s
              namespace: %s
            spec:
              channel: %s
              installPlanApproval: Automatic
              name: %s
              source: %s
              sourceNamespace: openshift-marketplace
            """, subscriptionName, deploymentNamespace, olmChannel, getAmqOperatorName(), brokerCatalogSourceName);

        LOGGER.info("[OLM] Creating Subscription");
        deployOlmResource(subscriptionString);

        TestUtils.waitFor("subscription to be active", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            List<Subscription> subs = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().inNamespace(deploymentNamespace).list().getItems();
            return subs != null && !subs.isEmpty();
        });

    }

    public void deployOperatorGroup() {
        HasMetadata operatorGroup = kubeClient.createOperatorGroup(deploymentNamespace, subscriptionName, watchedNamespaces);
        LOGGER.info("[OLM] Creating OperatorGroup");
        olmResources.add(operatorGroup);
    }

    private void deployOlmResource(String yamlStringConfig) {
        LOGGER.debug("[{}] [OLM] \n{} ", deploymentNamespace, yamlStringConfig);
        HasMetadata resource = kubeClient.getKubernetesClient().resource(yamlStringConfig).createOrReplace();
        olmResources.add(resource);
        DataStorer.dumpResourceToFile(resource);
    }

    public void deployOlmInstallation() {
        deployOperatorGroup();
        if (environmentOperator.isOlmReleased()) {
            deploySubscription(null);
        } else {
            deployCatalogSource(indexImageBundle);
            deploySubscription(olmChannel);
        }

        try {
            TestUtils.waitFor("broker-operator ClusterServiceVersion to be 'Succeeded'", Constants.DURATION_5_SECONDS, Constants.DURATION_2_MINUTES, () -> {
                    ClusterServiceVersion brokerCSV = kubeClient.getClusterServiceVersion(deploymentNamespace, amqBrokerOperatorName);
                    if (brokerCSV == null) return false;
                    LOGGER.debug("[{}] Checking for status phase of {}", deploymentNamespace, brokerCSV.getMetadata().getName());
                    return brokerCSV.getStatus().getPhase().equals("Succeeded");
                }
            );
            waitForCoDeployment();
            LOGGER.info("[{}] [OLM] Subscription & installplans successfully installed", deploymentNamespace);
        } catch (WaitException | KubernetesClientTimeoutException e) {
            LOGGER.error("[{}] Unable to deploy OLM based ClusterOperator! Undeploying", deploymentNamespace);
            undeployOperator(true);
        }
    }


    @Override
    public void deployOperator(boolean waitForDeployment) {
        LOGGER.info("[OLM] Deploying Artemis Cluster Operator in namespace {}", deploymentNamespace);
        deployOlmInstallation();
        LOGGER.info("[{}] [OLM] Cluster operator {} successfully deployed!", deploymentNamespace, operatorName);
    }

    @Override
    public void undeployOperator(boolean waitForUndeployment) {
        LOGGER.debug("[{}] Going to undeploy {}", deploymentNamespace, olmResources.stream().map(resource -> resource.getMetadata().getName()).toList());
        kubeClient.getKubernetesClient().resourceList(olmResources).delete();
        LOGGER.info("[{}] [OLM] Successfully undeployed ArtemisCloudOperator", deploymentNamespace);
    }

    public void updateSubscriptionEnvVar(String name, String value) {
        updateSubscriptionEnvVar(name, value, true);
    }

    public void updateSubscriptionEnvVar(String name, String value, boolean checkReadiness) {
        Subscription subscription = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().inNamespace(deploymentNamespace).withName(subscriptionName).get();
        SubscriptionConfig subscriptionConfig = subscription.getSpec().getConfig();
        if (subscriptionConfig == null) {
            // Create new SubscriptionConfig
            subscriptionConfig = new SubscriptionConfigBuilder()
                    .withEnv(List.of(
                            new EnvVarBuilder()
                                    .withName("ARGS")
                                    .withValue(name + "=" + value)
                                    .build()
                    )).build();
        } else {
            // Update existing EnvVars
            List<EnvVar> envVars = subscriptionConfig.getEnv();
            for (EnvVar envVar : envVars) {
                if (envVar.getName().equals("ARGS")) {
                    if (envVar.getValue().contains(name)) {
                        String newValue = envVar.getValue().replaceFirst(name + "=\\w+",
                                name + "=" + value);
                        envVar.setValue(newValue);
                    } else {
                        envVar.setValue(envVar.getValue() + " " + name + "=" + value);
                    }
                    subscriptionConfig.setEnv(envVars);
                }
            }
        }
        Pod operatorPod = kubeClient.getFirstPodByPrefixName(getDeploymentNamespace(), getOperatorName());
        subscription.getSpec().setConfig(subscriptionConfig);
        ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().resource(subscription).createOrReplace();
        kubeClient.waitForPodReload(getDeploymentNamespace(), operatorPod, getOperatorName(), checkReadiness);
    }

    @Override
    public void setOperatorLogLevel(String logLevel) {
        LOGGER.debug("[{}] Updating subscription {} with log level {}", getDeploymentNamespace(), subscriptionName, logLevel);
        updateSubscriptionEnvVar(ZAP_LOG_LEVEL_OPTION, logLevel);
    }

    @Override
    public void setOperatorLeaseDuration(int durationInSeconds, boolean waitForReadiness) {
        updateSubscriptionEnvVar(LEASE_DURATION_OPTION, String.valueOf(durationInSeconds), waitForReadiness);
    }

    @Override
    public void setOperatorRenewDeadlineDuration(int durationInSeconds, boolean waitForReadiness) {
        updateSubscriptionEnvVar(RENEW_DEADLINE_OPTION, String.valueOf(durationInSeconds), waitForReadiness);
    }

    @Override
    public void setOperatorRetryPeriodDuration(int durationInSeconds, boolean waitForReadiness) {
        updateSubscriptionEnvVar(RETRY_PERIOD_OPTION, String.valueOf(durationInSeconds), waitForReadiness);
    }

    protected void updateSubscription(Subscription updatedSubscription) {

    }

    public void deleteClusterServiceVersion() {
        List<ClusterServiceVersion> clusterServiceVersions = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().clusterServiceVersions().inNamespace(deploymentNamespace).list().getItems();
        for (ClusterServiceVersion csv : clusterServiceVersions) {
            LOGGER.info("[{}] [OLM] Deleting ClusterServiceVersion {}", deploymentNamespace, csv.getMetadata().getName());
            kubeClient.getKubernetesClient().resource(csv).inNamespace(deploymentNamespace).delete();
        }
    }

    public void deleteSubscription(String name) {
        List<Subscription> subs = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().inNamespace(deploymentNamespace).list().getItems();
        for (Subscription subscription: subs) {
            if (subscription.getMetadata().getName().equals(name)) {
                kubeClient.getKubernetesClient().resource(subscription).inNamespace(deploymentNamespace).delete();
                olmResources.remove(subscription);
            }
        }
    }

    public ArtemisCloudClusterOperatorOlm upgradeClusterOperator(String olmChannel, String indexImageBundle) {
        deployCatalogSource(indexImageBundle);
        this.indexImageBundle = indexImageBundle;
        if (!getOlmChannel().equals(olmChannel)) {
            deleteClusterServiceVersion();
            deleteSubscription(getSubscriptionName());

            TestUtils.threadSleep(Constants.DURATION_10_SECONDS);
            deploySubscription(olmChannel);
            this.olmChannel = olmChannel;
            waitForCoDeployment();
        }
        return this;
    }
}
