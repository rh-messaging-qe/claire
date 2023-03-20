/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
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
    protected List<HasMetadata> keycloakResources = new ArrayList<>();
    protected Map<String, String> admin = new HashMap<>();
    protected Pod keycloakSqlPod;
    protected Pod keycloakPod;
    protected final String kcadmCmd = "/opt/eap/bin/kcadm.sh";
    protected final String realmArtemisLdap = Constants.PROJECT_TEST_DIR + "/resources/keycloak/ldap_realm.json";
    protected final String adminUsernameKey = "ADMIN_USERNAME";
    protected final String adminPasswordKey = "ADMIN_PASSWORD";
    protected String deployRealmFilePath;
    private String realmsUrl = "http://localhost:8080/auth/admin/realms";
    private String kcadmCreateLdap = """
            %s create http://localhost:8080/auth/admin/realms/%s/components -r API \
            -s name="ldap-amq-broker" \
            -s parentId=%s \
            -s providerId=ldap \
            -s providerType=org.keycloak.storage.UserStorageProvider \
            -s 'config.priority=["1"]' \
            -s 'config.fullSyncPeriod=["-1"]' \
            -s 'config.changedSyncPeriod=["-1"]' \
            -s 'config.cachePolicy=["DEFAULT"]' \
            -s 'config.batchSizeForSync=["1000"]' \
            -s 'config.editMode=["READ_ONLY"]' \
            -s 'config.syncRegistrations=["false"]' \
            -s 'config.vendor=["ad"]' \
            -s 'config.usernameLDAPAttribute=["uid"]' \
            -s 'config.rdnLDAPAttribute=["cn"]' \
            -s 'config.uuidLDAPAttribute=["uid"]' \
            -s 'config.userObjectClasses=["inetOrgPerson"]' \
            -s 'config.connectionUrl=["ldap://openldap:1389"]' \
            -s 'config.usersDn=["ou=users,dc=example,dc=org"]' \
            -s 'config.authType=["simple"]' \
            -s 'config.bindDn=["cn=admin,dc=example,dc=org"]' \
            -s 'config.bindCredential=["admin"]' \
            -s 'config.searchScope=["1"]' \
            -s 'config.useTruststoreSpi=["ldapsOnly"]' \
            -s 'config.connectionPooling=["true"]' \
            -s 'config.pagination=["true"]' \
            -s 'config.allowKerberosAuthentication=["false"]' \
            -s 'config.debug=["true"]' \
            -s 'config.useKerberosForPasswordAuthentication=["false"]'
            """;
    String kcAdmCreateLdapRoleMapper = """
                %s create http://localhost:8080/auth/admin/realms/%s/components -r API \
                -s parentId=%s \
                -s name="ldap-roles" \
                -s providerId=role-ldap-mapper \
                -s providerType=org.keycloak.storage.ldap.mappers.LDAPStorageMapper \
                -s 'config."mode"=[ "READ_ONLY" ]' \
                -s 'config."membership.attribute.type"=[ "DN" ]' \
                -s 'config."user.roles.retrieve.strategy"=[ "LOAD_ROLES_BY_MEMBER_ATTRIBUTE" ]' \
                -s 'config."roles.dn"=[ "ou=users,dc=example,dc=org" ]' \
                -s 'config."membership.ldap.attribute"=[ "member" ]' \
                -s 'config."membership.user.ldap.attribute"=[ "uid" ]' \
                -s 'config."role.name.ldap.attribute"=[ "cn" ]' \
                -s 'config."memberof.ldap.attribute"=[ "member" ]' \
                -s 'config."use.realm.roles.mapping"=[ "true" ]' \
                -s 'config."role.object.classes"=[ "groupOfNames" ]'
                """;

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
        String loginCommand = String.format("%s config credentials --server http://localhost:8080/auth --realm master --user %s --password %s",
                kcadmCmd, admin.get(adminUsernameKey), admin.get(adminPasswordKey));
        kubeClient.executeCommandInPod(namespace, keycloakPod, loginCommand, Constants.DURATION_10_SECONDS);
    }

    public void undeployOperator() {
        kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).delete();
        if (deployRealmFilePath != null) {
            TestUtils.deleteFile(Paths.get(deployRealmFilePath));
        }
        LOGGER.info("[{}] [KC] Successfully undeployed Keycloak.", namespace);
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

    public void importRealm(String realmName, String realmFilePath) {
        LOGGER.debug("[{}] [KC] Importing realm {} from file {}", namespace, realmName, realmFilePath);
        String realmPodFilePath = "/tmp/" + realmName + "_realm.json";
        kubeClient.getKubernetesClient().pods().inNamespace(namespace).withName(keycloakPod.getMetadata().getName()).file(realmPodFilePath).upload(Paths.get(realmFilePath));

        String createImportRealm = String.format("%s create realms -s realm=%s -s enabled=true &&" +
                " %s create partialImport -r %s -s ifResourceExists=SKIP -o -f %s", kcadmCmd, realmName, kcadmCmd, realmName, realmPodFilePath);
        kubeClient.executeCommandInPod(namespace, keycloakPod, createImportRealm, Constants.DURATION_1_MINUTE);
    }

    public void setupLdapModule(String realmName) {
        LOGGER.info("[{}] [KC] Setup LDAP Modules in realm {}", namespace, realmName);
        String kcAdmParentId = String.format("%s get %s/%s --fields id --format csv --noquotes", kcadmCmd, realmsUrl, realmName);
        String parentId = kubeClient.executeCommandInPod(namespace, keycloakPod, kcAdmParentId, Constants.DURATION_1_MINUTE).replace("\n", "");

        String result = kubeClient.executeCommandInPod(namespace, keycloakPod, String.format(kcadmCreateLdap, kcadmCmd, realmName, parentId), Constants.DURATION_1_MINUTE);
        String ldapId = result.substring(result.indexOf("'") + 1, result.lastIndexOf("'"));

        LOGGER.info("[{}] [KC] Create LDAP role mapper into realm {}", namespace, realmName);
        kubeClient.executeCommandInPod(namespace, keycloakPod, String.format(kcAdmCreateLdapRoleMapper, kcadmCmd, realmName, ldapId), Constants.DURATION_1_MINUTE);

        LOGGER.info("[{}] [KC] Import LDAP users into realm {}", namespace, realmName);
        String syncUsersCmd = String.format("%s create %s/%s/user-storage/%s/sync?action=triggerFullSync", kcadmCmd, realmsUrl, realmName, ldapId);
        kubeClient.executeCommandInPod(namespace, keycloakPod, syncUsersCmd, Constants.DURATION_1_MINUTE);
    }

    public void setupRedirectUris(String realm, String clientName, ActiveMQArtemis broker) {
        // Update redirectUris with current webconsole routes (ex-aao-wconsj-0-svc-rte-oauth-tests)
        String clientId = getClientId(realm, clientName);
        LOGGER.debug("[{}] [KC] Update redirectUris in realm {} {}", namespace, realm, clientName);
        List<String> uris = kubeClient.getExternalAccessServiceUrlPrefixName(
                namespace, broker.getMetadata().getName() + "-" + Constants.WEBCONSOLE_URI_PREFIX + "-");
        String format = broker.getSpec().getConsole().getSslEnabled() ? "https://ROUTE/console/*" : "http://ROUTE/console/*";

        StringBuilder constructRoutes = new StringBuilder();
        for (String uri : uris) {
            constructRoutes.append("\"").append(format.replaceAll("ROUTE", uri)).append("\", ");
        }
        constructRoutes.deleteCharAt(constructRoutes.lastIndexOf(","));

        LOGGER.info("[{}] [KC] Constructed routes\n{}", namespace, constructRoutes);
        String updateRedirectUris = String.format("%s update %s/%s/clients/%s/ -s 'redirectUris=[%s]'", kcadmCmd, realmsUrl, realm, clientId, constructRoutes);
        kubeClient.executeCommandInPod(namespace, keycloakPod, updateRedirectUris, Constants.DURATION_10_SECONDS);
    }

    public String getClientSecretId(String realm, String clientName) {
        // https://www.keycloak.org/docs/16.1/securing_apps/#client-id-and-client-secret
        String clientId = getClientId(realm, clientName);
        String clientSecretCmd = String.format("%s get %s/%s/clients/%s/client-secret | grep value | tr -d ' \",' | cut -d ':' -f 2",
                kcadmCmd, realmsUrl, realm, clientId);
        String clientSecretId = kubeClient.executeCommandInPod(namespace, keycloakPod, clientSecretCmd, Constants.DURATION_10_SECONDS).strip();
        if (clientSecretId.equals("**********")) {
            LOGGER.debug("[{}] [KC] Generate new {} client-secret as it is empty", namespace, clientName);
            String clientGenerateSecretCmd = String.format("%s create %s/%s/clients/%s/client-secret", kcadmCmd, realmsUrl, realm, clientId);
            kubeClient.executeCommandInPod(namespace, keycloakPod, clientGenerateSecretCmd, Constants.DURATION_10_SECONDS);
            clientSecretId = kubeClient.executeCommandInPod(namespace, keycloakPod, clientSecretCmd, Constants.DURATION_10_SECONDS).strip();
        }
        LOGGER.debug("[{}] [KC] Using {} client-secret {}", namespace, realm, clientSecretId);
        return clientSecretId;
    }

    private String getClientId(String realm, String clientName) {
        String keycloakClientIdCmd = String.format("%s get %s/%s/clients?clientId=%s | grep '\"id\"' | tr -d ' \",' | cut -d ':' -f 2",
                kcadmCmd, realmsUrl, realm, clientName);
        String clientId = kubeClient.executeCommandInPod(namespace, keycloakPod, keycloakClientIdCmd, Constants.DURATION_10_SECONDS).strip();
        LOGGER.debug("[{}] [KC] Found clientId {} for realm {} ", namespace, clientId, realm);
        return clientId;
    }

    public String getAuthUri() {
        return "https://" + kubeClient.getExternalAccessServiceUrl(namespace, "keycloak") + "/auth";
    }
}
