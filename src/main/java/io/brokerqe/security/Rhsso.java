/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.brokerqe.Constants;
import io.brokerqe.Environment;
import io.brokerqe.KubeClient;
import io.brokerqe.TestUtils;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import io.fabric8.openshift.client.OpenShiftClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class Rhsso extends Keycloak {

    private static final Logger LOGGER = LoggerFactory.getLogger(Rhsso.class);

    public Rhsso(Environment testEnvironment, KubeClient kubeClient, String namespace) {
        super(testEnvironment, kubeClient, namespace);
    }

    public void deployOperator() {
        // setupKeycloak();
        applyOperatorGroup();
        applySubscription();
        applyKeycloakResources();
        setupAdminLogin();
    }

    private void applyOperatorGroup() {
        String operatorGroupString = String.format(
            """
            apiVersion: operators.coreos.com/v1
            kind: OperatorGroup
            metadata:
              name: broker-group
              namespace: %s
            spec:
              targetNamespaces:
                - %s""", namespace, namespace);

        HasMetadata operatorGroup = kubeClient.getKubernetesClient().resource(operatorGroupString).inNamespace(namespace).createOrReplace();
        kubeClient.getKubernetesClient().resourceList(operatorGroup).inNamespace(namespace).createOrReplace();
        keycloakResources.add(operatorGroup);
    }

    private void applySubscription() {
        String subscriptionString = String.format(
            """
            apiVersion: operators.coreos.com/v1alpha1
            kind: Subscription
            metadata:
              name: rhsso-operator-my
              namespace: %s
            spec:
              channel: stable
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
              instances: 1""", namespace);

        HasMetadata keycloak = kubeClient.getKubernetesClient().resource(keycloakString).inNamespace(namespace).createOrReplace();
        keycloakResources.add(keycloak);

        TestUtils.waitFor("[KC] Keycloak pods to show up", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () ->
                kubeClient.getFirstPodByPrefixName(namespace, "keycloak-0") != null &&
                        kubeClient.getFirstPodByPrefixName(namespace, "keycloak-postgresql") != null);

        keycloakPod = kubeClient.getFirstPodByPrefixName(namespace, "keycloak-0");
        keycloakSqlPod = kubeClient.getFirstPodByPrefixName(namespace, "keycloak-postgresql");
        LOGGER.info("[{}] Waiting for pods {} and {} to be ready", namespace, keycloakPod.getMetadata().getName(), keycloakSqlPod.getMetadata().getName());
        // TODO: Sometimes keycloak fails to start -> removing pod helped
        kubeClient.getKubernetesClient().pods().inNamespace(namespace).resource(keycloakSqlPod).waitUntilReady(5, TimeUnit.MINUTES);
        try {
            kubeClient.getKubernetesClient().pods().inNamespace(namespace).resource(keycloakPod).waitUntilReady(3, TimeUnit.MINUTES);
        } catch (KubernetesClientTimeoutException e) {
//            keycloakPod sometimes fails to start on some weird exception. Restart it if such log is present
            restartStuckKeycloakPod(e);
        }
        LOGGER.info("[{}] Keycloak pods are ready", namespace);
    }

    public void importRealm(String realmName, String realmFilePath, String brokerName) {
        // oc cp claire/src/test/resources/keycloak/internal_realm.json oauth-tests/keycloak-0:/tmp/ -c keycloak
        // kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user admin --password l_dtMCUpuZU0iw==
        // /opt/eap/bin/kcadm.sh create realms -s realm=internal -s enabled=true
        // /opt/eap/bin/kcadm.sh create partialImport -r internal -s ifResourceExists=FAIL -o -f /tmp/internal_realm.json
        realmFilePath = updateRealmImportTemplate(realmFilePath, brokerName);
        LOGGER.debug("[{}] [KC] Importing realm {} from file {}", namespace, realmName, realmFilePath);
        String realmPodFilePath = "/tmp/" + realmName + "_realm.json";

        // Update redirectUris with current webconsole routes ex-aao-wconsj-0-svc-rte-oauth-tests
        kubeClient.getKubernetesClient().pods().inNamespace(namespace).withName(keycloakPod.getMetadata().getName()).file(realmPodFilePath).upload(Paths.get(realmFilePath));

        String createImportRealm = String.format("/opt/eap/bin/kcadm.sh create realms -s realm=%s -s enabled=true &&" +
                " /opt/eap/bin/kcadm.sh create partialImport -r %s -s ifResourceExists=SKIP -o -f %s", realmName, realmName, realmPodFilePath);
        kubeClient.executeCommandInPod(namespace, keycloakPod, createImportRealm, Constants.DURATION_1_MINUTE);
    }
}
