/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.operator;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.exception.ClaireRuntimeException;
import io.brokerqe.claire.exception.WaitException;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageChannel;
import io.fabric8.openshift.api.model.operatorhub.lifecyclemanager.v1.PackageManifest;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.ClusterServiceVersion;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionConfig;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.SubscriptionConfigBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ArtemisCloudClusterOperatorOlm extends ArtemisCloudClusterOperator {
    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperatorOlm.class);
    private String olmChannel;
    private String indexImageBundle;
    private String brokerCatalogSourceName = "broker-source-" + TestUtils.getRandomString(2);
    private String subscriptionName = "amq-broker-rhel8-" + TestUtils.getRandomString(2);
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

    public String getOlmChannel() {
        return olmChannel;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    private void getPackageManifestChannel(boolean isLts) {
        String olmCSV;
        PackageChannel nonLtsChannel;
        PackageChannel ltsChannel;

        PackageManifest amqBrokerPM = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub()
                .packageManifests().inNamespace(sourceNamespace).withName("amq-broker-rhel8").get();
        if (!amqBrokerPM.getStatus().getCatalogSource().equals(brokerCatalogSourceName)) {
            LOGGER.error("[{}] Found unexpected CatalogSource for `amq-broker-rhel8` {}!", deploymentNamespace, amqBrokerPM.getStatus().getCatalogSource());
            throw new ClaireRuntimeException("Discovered unexpected CatalogSource " + amqBrokerPM.getStatus().getCatalogSource() + "!");
        }

        String defaultChannel = amqBrokerPM.getStatus().getDefaultChannel(); //non-lts
        if (amqBrokerPM.getStatus().getChannels().get(0).getName().equals(defaultChannel)) {
            nonLtsChannel = amqBrokerPM.getStatus().getChannels().get(0);
            ltsChannel = amqBrokerPM.getStatus().getChannels().get(1);
        } else {
            nonLtsChannel = amqBrokerPM.getStatus().getChannels().get(1);
            ltsChannel = amqBrokerPM.getStatus().getChannels().get(0);
        }
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
              name: amq-broker-rhel8
              source: %s
              sourceNamespace: openshift-marketplace
            """, subscriptionName, deploymentNamespace, olmChannel, brokerCatalogSourceName);

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

    @Override
    public void setOperatorLogLevel(String logLevel) {
        LOGGER.debug("[{}] Updating subscription {} with log level {}", getDeploymentNamespace(), subscriptionName, logLevel);
        Subscription subscription = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().inNamespace(deploymentNamespace).withName(subscriptionName).get();
        SubscriptionConfig subscriptionConfig = subscription.getSpec().getConfig();
        if (subscriptionConfig == null) {
            // Create new SubscriptionConfig
            subscriptionConfig = new SubscriptionConfigBuilder()
                .withEnv(List.of(
                    new EnvVarBuilder()
                        .withName("ARGS")
                        .withValue(ZAP_LOG_LEVEL_OPTION + "=" + logLevel)
                        .build()
                )).build();
        } else {
            // Update existing EnvVars
            List<EnvVar> envVars = subscriptionConfig.getEnv();
            for (EnvVar envVar : envVars) {
                if (envVar.getName().equals("ARGS")) {
                    if (envVar.getValue().contains(ZAP_LOG_LEVEL_OPTION)) {
                        String newValue = envVar.getValue().replaceFirst(ZAP_LOG_LEVEL_OPTION + "=\\w+",
                                ZAP_LOG_LEVEL_OPTION + "=" + logLevel);
                        envVar.setValue(newValue);
                    } else {
                        envVar.setValue(envVar.getValue() + " " + ZAP_LOG_LEVEL_OPTION + "=" + logLevel);
                    }
                    subscriptionConfig.setEnv(envVars);
                }
            }
        }
        subscription.getSpec().setConfig(subscriptionConfig);
        ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().resource(subscription).createOrReplace();
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
