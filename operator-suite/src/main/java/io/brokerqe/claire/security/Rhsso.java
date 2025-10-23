/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentOperator;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.TestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.openshift.api.model.operatorhub.v1.OperatorGroup;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;

public class Rhsso extends Keycloak {

    private static final Logger LOGGER = LoggerFactory.getLogger(Rhsso.class);

    public Rhsso(EnvironmentOperator testEnvironmentOperator, KubeClient kubeClient, String namespace) {
        super(testEnvironmentOperator, kubeClient, namespace);
    }

    @Override
    public Rhsso deploy() {
        // setupKeycloak();
        applyOperatorGroup();
        applySubscription();
        deployPostgres();
        applyKeycloakResources();
        setupAdminLogin();
        return this;
    }

    @Override
    public void undeploy() {
        if (keycloakResources != null) {
            keycloakResources.forEach(resource -> {
                if (resource != null) {
                    LOGGER.info("[KC] Removing {}", resource.getMetadata().getName());
                    kubeClient.getKubernetesClient().resource(resource).inNamespace(namespace).delete();
                }
            });
            // TODO: sometimes this fails on NPE on keycloakResources
            // kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).delete();
        } else {
            LOGGER.error("[{}] [KC] keycloakResources are null!", namespace);
        }
        if (deployRealmFilePath != null) {
            TestUtils.deleteFile(Paths.get(deployRealmFilePath));
        }
        LOGGER.info("[{}] [KC] Successfully undeployed Keycloak.", namespace);
    }

    private void applyOperatorGroup() {
        List<OperatorGroup> operatorGroups = kubeClient.getOperatorGroups(namespace);
        if (operatorGroups.size() > 0) {
            LOGGER.info("[{}] [KC] Skipping creation of operatorGroup as it already exists in this namespace", namespace);
            for (OperatorGroup og : operatorGroups) {
                LOGGER.debug("[{}] Existing operator group: {}", namespace, og.getMetadata().getName());
            }
        } else {
            HasMetadata operatorGroup = kubeClient.createOperatorGroup(namespace, "broker-group");
            keycloakResources.add(operatorGroup);
        }
    }

    private void applySubscription() {
        String rhbkOperatorName = testEnvironmentOperator.getKeycloakOperatorName();
        String keycloakChannel = testEnvironmentOperator.getKeycloakChannel();
        String keycloakVersion = testEnvironmentOperator.getKeycloakVersion();
//        keycloakChannel = "stable-v26";
//        keycloakVersion = "rhbk-operator.v26.0.10-opr.1";

        String subscriptionString = String.format(
            """
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
              name: rhbk-operator-my
              namespace: %s
            spec:
              channel: %s
              installPlanApproval: Automatic
              name: %s
              source: redhat-operators
              startingCSV: %s
              sourceNamespace: openshift-marketplace""", namespace, keycloakChannel, rhbkOperatorName, keycloakVersion);

        HasMetadata subscription = kubeClient.getKubernetesClient().resource(subscriptionString).inNamespace(namespace).createOrReplace();
        LOGGER.debug("[RHSSO]\n{}", subscriptionString);
        keycloakResources.add(subscription);
        TestUtils.waitFor("rh-sso operator deployment to be ready", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            Deployment operatorRhsso = kubeClient.getDeployment(namespace, rhbkOperatorName);
            return operatorRhsso != null && operatorRhsso.getStatus().getReadyReplicas().equals(operatorRhsso.getSpec().getReplicas());
        });
        Deployment operatorRhsso = kubeClient.getDeployment(namespace, rhbkOperatorName);
        keycloakResources.add(operatorRhsso);

        LOGGER.info("[{}] Deployed Subscription for {} with ClusterServiceVersion {}", namespace, rhbkOperatorName, keycloakVersion);
        keycloakResources.add(((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().clusterServiceVersions().inNamespace(namespace).withName(keycloakVersion).get());
    }

}
