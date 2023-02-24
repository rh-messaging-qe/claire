/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.operator;

import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.openshift.api.model.operatorhub.v1alpha1.Subscription;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ArtemisCloudClusterOperatorOlm extends ArtemisCloudClusterOperator {
    final static Logger LOGGER = LoggerFactory.getLogger(ArtemisCloudClusterOperatorOlm.class);
    private final String olmChannel;
    private final String indexImageBundle;
    private List<HasMetadata> olmResources = new ArrayList<>();

    public ArtemisCloudClusterOperatorOlm(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces) {
        this(deploymentNamespace, isNamespaced, watchedNamespaces, ResourceManager.getEnvironment().getOlmIndexImageBundle(), ResourceManager.getEnvironment().getOlmChannel());
    }

    public ArtemisCloudClusterOperatorOlm(String deploymentNamespace, boolean isNamespaced, List<String> watchedNamespaces, String indexImageBundle, String olmChannel) {
        super(deploymentNamespace, isNamespaced, watchedNamespaces);
        this.indexImageBundle = indexImageBundle;
        this.olmChannel = olmChannel;
    }

    public void deployOlmInstallation() {
        String brokerSourceName = "broker-source-" + TestUtils.getRandomString(2);
        String subscriptionName = "amq-broker-rhel8-" + TestUtils.getRandomString(2);

        String catalogSource = String.format("""
            apiVersion: operators.coreos.com/v1alpha1
            kind: CatalogSource
            metadata:
              name: %s
              namespace: openshift-marketplace
            spec:
              sourceType: grpc
              image: %s           
            """, brokerSourceName, indexImageBundle);

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
            """, subscriptionName, deploymentNamespace, olmChannel, brokerSourceName);

        String watchedNamespacesString;
        if (watchedNamespaces != null) {
            watchedNamespacesString = String.join("\n    - ", watchedNamespaces);
        } else {
            watchedNamespacesString = deploymentNamespace;
        }
        String operatorGroup = String.format("""
            apiVersion: operators.coreos.com/v1
            kind: OperatorGroup
            metadata:
              name: %s
              namespace: %s
            spec:
              targetNamespaces:
                - %s
            """, subscriptionName, deploymentNamespace, watchedNamespacesString);

        LOGGER.info("[OLM] Creating CatalogSource");
        LOGGER.debug(catalogSource);
        olmResources.add(kubeClient.getKubernetesClient().resource(catalogSource).createOrReplace());

        LOGGER.info("[OLM] Creating OperatorGroup");
        LOGGER.debug("[{}] [OLM] {} ", deploymentNamespace, operatorGroup);
        olmResources.add(kubeClient.getKubernetesClient().resource(operatorGroup).inNamespace(deploymentNamespace).createOrReplace());

        LOGGER.info("[OLM] Creating Subscription");
        LOGGER.debug("[{}] [OLM] {} ", deploymentNamespace, subscriptionString);
        Subscription subscription = (Subscription) kubeClient.getKubernetesClient().resource(subscriptionString).inNamespace(deploymentNamespace).createOrReplace();
        olmResources.add(subscription);
        // waitFor
        TestUtils.waitFor("subscription to be active", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            List<Subscription> subs = ((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().subscriptions().inNamespace(deploymentNamespace).list().getItems();
            return subs != null && subs.size() > 0;
        });

        TestUtils.waitFor("deployment to be active", Constants.DURATION_5_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            return kubeClient.getDeployment(deploymentNamespace, operatorName) != null;
        });

        LOGGER.info("[{}] [OLM] Subscription & installplans sucessfully installed", deploymentNamespace);
    }


    @Override
    public void deployOperator(boolean waitForDeployment) {
        LOGGER.info("[OLM] Deploying Artemis Cluster Operator in namespace {}", deploymentNamespace);
        deployOlmInstallation();
        // wait for installplans
        if (waitForDeployment) {
            waitForCoDeployment();
        }

        LOGGER.info("[{}] [OLM] Cluster operator {} successfully deployed!", deploymentNamespace, operatorName);
    }

    @Override
    public void undeployOperator(boolean waitForUndeployment) {
        kubeClient.getKubernetesClient().resourceList(olmResources).delete();
        LOGGER.info("[{}] [OLM] Successfully undeployed ArtemisCloudOperator", deploymentNamespace);
    }
}
