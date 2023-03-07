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
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Keycloak {

    private static final Logger LOGGER = LoggerFactory.getLogger(Keycloak.class);
    protected final Environment testEnvironment;
    protected final KubeClient kubeClient;
    protected final String namespace;
    protected final String keycloakVersion;
    protected final List<HasMetadata> keycloakResources = new ArrayList<>();
    protected Map<String, String> admin = new HashMap<>();
    protected Pod keycloakSqlPod;
    protected Pod keycloakPod;
    protected final String realmArtemis = Constants.PROJECT_TEST_DIR + "/resources/keycloak/amqbroker_realm.json_template";
    protected final String realmArtemisLdap = Constants.PROJECT_TEST_DIR + "/resources/keycloak/ldap_realm.json";
    protected final String adminUsernameKey = "ADMIN_USERNAME";
    protected final String adminPasswordKey = "ADMIN_PASSWORD";
    protected String deployRealmFilePath;

    public Keycloak(Environment testEnvironment, KubeClient kubeClient, String namespace) {
        this.testEnvironment = testEnvironment;
        this.kubeClient = kubeClient;
        this.namespace = namespace;
        this.keycloakVersion = testEnvironment.getKeycloakVersion();
    }

    public void deployOperator() {
        setupKeycloakOperator();
//            applyKeycloakResources(); // TODO
        setupAdminLogin();
    }

    protected void setupAdminLogin() {
        Map<String, String> adminPassword = kubeClient.getSecret(namespace, "credential-example-keycloak").getData();
        admin.put(adminUsernameKey, TestUtils.getDecodedBase64String(adminPassword.get(adminUsernameKey)));
        admin.put(adminPasswordKey, TestUtils.getDecodedBase64String(adminPassword.get(adminPasswordKey)));

        LOGGER.info("[{}] [KC] Using login credentials {}/{}", namespace, admin.get(adminUsernameKey), admin.get(adminPasswordKey));
        String loginCommand = String.format("/opt/eap/bin/kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user %s --password %s",
                admin.get(adminUsernameKey), admin.get(adminPasswordKey));
        kubeClient.executeCommandInPod(namespace, keycloakPod, loginCommand, Constants.DURATION_10_SECONDS);
    }

    public void undeployOperator() {
        kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).delete();
        TestUtils.deleteFile(Paths.get(deployRealmFilePath));
        LOGGER.info("[{}] [KC] Successfully undeployed Keycloak.", namespace);
    }

    protected String updateRealmImportTemplate(String realmFilePath, String brokerName) {
        List<String> uris = kubeClient.getExternalAccessServiceUrlPrefixName(namespace, brokerName + "-" + Constants.WEBCONSOLE_URI_PREFIX + "-");
        String format = "        \"https://ROUTE/console/*\",\n";
        StringBuilder constructRoutes = new StringBuilder();
        for (String uri : uris) {
            constructRoutes.append(format.replaceAll("ROUTE", uri));
        }
        constructRoutes.deleteCharAt(constructRoutes.lastIndexOf(","));
        LOGGER.warn("[{}] [KC] Constructed routes\n{}", namespace, constructRoutes);

        deployRealmFilePath = Constants.PROJECT_TEST_DIR + "resources/keycloak/amqbroker_realm_" + TestUtils.getRandomString(3) + ".json";

        File jsonRealmfile = new File(realmFilePath);
        String data = TestUtils.readFileContent(jsonRealmfile);
        data = data.replace("ROUTES", constructRoutes);
        TestUtils.createFile(deployRealmFilePath, data);
        return deployRealmFilePath;
    }

    public void importRealm(String realmName, String realmFilePath, String brokerName, boolean updateRealm) {
        // oc cp claire/src/test/resources/keycloak/internal_realm.json oauth-tests/keycloak-0:/tmp/ -c keycloak
        // kcadm.sh config credentials --server http://localhost:8080/auth --realm master --user admin --password l_dtMCUpuZU0iw==
        // /opt/eap/bin/kcadm.sh create realms -s realm=internal -s enabled=true
        // /opt/eap/bin/kcadm.sh create partialImport -r internal -s ifResourceExists=FAIL -o -f /tmp/internal_realm.json
        if (updateRealm) {
            realmFilePath = updateRealmImportTemplate(realmFilePath, brokerName);
        }
        LOGGER.debug("[{}] [KC] Importing realm {} from file {}", namespace, realmName, realmFilePath);
        String realmPodFilePath = "/tmp/" + realmName + "_realm.json";

        // Update redirectUris with current webconsole routes ex-aao-wconsj-0-svc-rte-oauth-tests
        kubeClient.getKubernetesClient().pods().inNamespace(namespace).withName(keycloakPod.getMetadata().getName()).file(realmPodFilePath).upload(Paths.get(realmFilePath));

        String createImportRealm = String.format("/opt/eap/bin/kcadm.sh create realms -s realm=%s -s enabled=true &&" +
                " /opt/eap/bin/kcadm.sh create partialImport -r %s -s ifResourceExists=SKIP -o -f %s", realmName, realmName, realmPodFilePath);
        kubeClient.executeCommandInPod(namespace, keycloakPod, createImportRealm, Constants.DURATION_1_MINUTE);
    }

    void restartStuckKeycloakPod(KubernetesClientTimeoutException exception) {
        String kcLog = kubeClient.getKubernetesClient().pods().resource(keycloakPod).getLog();
        if (kcLog.contains("failed to match pool. Check JndiName:")) {
            LOGGER.warn("[{}] [KC] {} failed to start in time. Restarting.", namespace, keycloakPod.getMetadata().getName());
            kubeClient.getKubernetesClient().pods().resource(keycloakPod).delete();
            TestUtils.waitFor("[KC] Keycloak pods to show up", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () ->
                    kubeClient.getFirstPodByPrefixName(namespace, "keycloak-0") != null);
            keycloakPod = kubeClient.getFirstPodByPrefixName(namespace, "keycloak-0");
            kubeClient.getKubernetesClient().pods().inNamespace(namespace).resource(keycloakPod).waitUntilReady(3, TimeUnit.MINUTES);
        } else {
            throw exception;
        }
    }

    private void setupKeycloakOperator() {
        try {
            kubeClient.listPods(namespace);
            String kcVersion = testEnvironment.getKeycloakVersion();
            LOGGER.info("[{}] [KC] Deploying Keycloak {}", namespace, kcVersion);

            // Load and create Keycloak resources
            URL keycloakCrdUrl = new URL(String.format("https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/%s/kubernetes/keycloaks.k8s.keycloak.org-v1.yml", keycloakVersion));
            URL keycloakRealmImportsCrdUrl = new URL(String.format("https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/%s/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml", keycloakVersion));
            URL kcDeploymentFileUrl = new URL(String.format("https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/%s/kubernetes/kubernetes.yml", keycloakVersion));

            // Load Yaml into Kubernetes resources
            LOGGER.debug("[{}] [KC] Loading and creating keycloak resources", namespace);
            keycloakResources.add(kubeClient.getKubernetesClient().apiextensions().v1().customResourceDefinitions().load(keycloakCrdUrl).get());
            keycloakResources.add(kubeClient.getKubernetesClient().apiextensions().v1().customResourceDefinitions().load(keycloakRealmImportsCrdUrl).get());
            keycloakResources.addAll(kubeClient.getKubernetesClient().load(kcDeploymentFileUrl.openStream()).get());
            // Apply Kubernetes Resources
            kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).createOrReplace();

            // Wait for replicaSet ready
            TestUtils.waitFor("Keycloak replicaset to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_5_MINUTES, () -> {
                List<ReplicaSet> rSets = kubeClient.getReplicaSetsWithPrefix(namespace, "keycloak-operator");
                return rSets != null && rSets.size() > 0 &&
                        rSets.get(0).getStatus().getReadyReplicas().equals(rSets.get(0).getSpec().getReplicas());
            });
            LOGGER.info("[{}] [KC] Successfully deployed Keycloak Operator.", namespace);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
