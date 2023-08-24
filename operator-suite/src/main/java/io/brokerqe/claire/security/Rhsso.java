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
import java.util.Map;

public class Rhsso extends Keycloak {

    private static final Logger LOGGER = LoggerFactory.getLogger(Rhsso.class);
    protected final String kcadmCmd = "/opt/eap/bin/kcadm.sh";

    public Rhsso(EnvironmentOperator testEnvironmentOperator, KubeClient kubeClient, String namespace) {
        super(testEnvironmentOperator, kubeClient, namespace);
    }

    public void deployOperator() {
        // setupKeycloak();
        applyOperatorGroup();
        applySubscription();
        applyKeycloakResources();
        setupAdminLogin();
    }

    public void undeployOperator() {
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
        // TODO: remove spec.config once 7.6.4 is fixed https://issues.redhat.com/browse/SSOSUP-560
        String subscriptionString = String.format(
            """
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
              name: rhsso-operator-my
              namespace: %s
            spec:
              channel: stable
              config:
                env:
                - name: RELATED_IMAGE_POSTGRESQL
                  value: registry.redhat.io/rhel8/postgresql-10:1-217
              installPlanApproval: Automatic
              name: rhsso-operator
              source: redhat-operators
              startingCSV: %s
              sourceNamespace: openshift-marketplace""", namespace, keycloakVersion);

        HasMetadata subscription = kubeClient.getKubernetesClient().resource(subscriptionString).inNamespace(namespace).createOrReplace();
        keycloakResources.add(subscription);
        TestUtils.waitFor("rh-sso operator deployment to be ready", Constants.DURATION_10_SECONDS, Constants.DURATION_5_MINUTES, () -> {
            Deployment operatorRhsso = kubeClient.getDeployment(namespace, "rhsso-operator");
            return operatorRhsso != null && operatorRhsso.getStatus().getReadyReplicas().equals(operatorRhsso.getSpec().getReplicas());
        });
        Deployment operatorRhsso = kubeClient.getDeployment(namespace, "rhsso-operator");
        keycloakResources.add(operatorRhsso);

        LOGGER.info("[{}] Deployed Subscription for rhsso-operator with clusterserviceversion {}", namespace, keycloakVersion);
        keycloakResources.add(((OpenShiftClient) kubeClient.getKubernetesClient()).operatorHub().clusterServiceVersions().inNamespace(namespace).withName(keycloakVersion).get());
    }

    private void applyKeycloakResources() {
        String keycloakString = String.format(
            """
            apiVersion: keycloak.org/v1alpha1
            kind: Keycloak
            metadata:
              name: example-keycloak
              labels:
                app: sso
              namespace: %s
            spec:
              externalAccess:
                enabled: true
              instances: 1
              additionalOptions:
                - name: log-level
                  value: debug
                - name: http-enabled
                  value: "true"
            """, namespace);

        HasMetadata keycloak = kubeClient.getKubernetesClient().resource(keycloakString).inNamespace(namespace).createOrReplace();
        keycloakResources.add(keycloak);

        waitForKeycloakDeployment("keycloak-0", "keycloak-postgresql");
    }

    protected void setupAdminLogin() {
        Map<String, String> adminPassword = kubeClient.getSecret(namespace, "credential-example-keycloak").getData();
        admin.put(adminUsernameKey, TestUtils.getDecodedBase64String(adminPassword.get(adminUsernameKey)));
        admin.put(adminPasswordKey, TestUtils.getDecodedBase64String(adminPassword.get(adminPasswordKey)));

        LOGGER.info("[{}] [KC] Using login credentials {}/{}", namespace, admin.get(adminUsernameKey), admin.get(adminPasswordKey));
        String loginCommand = String.format("%s config credentials --server http://localhost:8080/auth --realm master --user %s --password %s %s",
                getKcadmCmd(), admin.get(adminUsernameKey), admin.get(adminPasswordKey), tmpConfig);
        kubeClient.executeCommandInPod(keycloakPod, loginCommand, Constants.DURATION_10_SECONDS);
    }

    protected String getKcadmCmd() {
        return kcadmCmd;
    }

    public String getAuthUri() {
        return "https://" + kubeClient.getExternalAccessServiceUrl(namespace, "keycloak") + "/auth";
    }

}
