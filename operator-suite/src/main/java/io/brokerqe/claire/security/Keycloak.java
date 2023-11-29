/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.EnvironmentOperator;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.db.Postgres;
import io.brokerqe.claire.helpers.DataStorer;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClientTimeoutException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Keycloak {

    private static final Logger LOGGER = LoggerFactory.getLogger(Keycloak.class);
    protected final EnvironmentOperator testEnvironmentOperator;
    protected final KubeClient kubeClient;
    protected final String namespace;

    private Postgres postgres;
    protected final String keycloakVersion;
    protected List<HasMetadata> keycloakResources = new ArrayList<>();
    protected Map<String, String> admin = new HashMap<>();
    protected Pod keycloakSqlPod;
    protected Pod keycloakPod;
    protected final String kcadmCmd = "/opt/keycloak/bin/kcadm.sh";
    public final String realmArtemisLdap = Constants.PROJECT_TEST_DIR + "/resources/keycloak/ldap_realm.json";
    protected final String adminUsernameKey = "ADMIN_USERNAME";
    protected final String adminPasswordKey = "ADMIN_PASSWORD";
    protected String deployRealmFilePath;
    protected String realmsUrl = "http://localhost:8080/auth/admin/realms";
    protected String tokenUrlTemplate = "%s/realms/%s/protocol/openid-connect/token";
    protected String tmpConfig = "--config /tmp/kcadm.config";
    protected String kcadmCreateLdap = """
            %s create %s -r API \
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
            -s 'config.useKerberosForPasswordAuthentication=["false"]' \
            %s
            """;
    protected String kcAdmCreateLdapRoleMapper = """
                %s create realms/%s/components -r API \
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
                -s 'config."role.object.classes"=[ "groupOfNames" ]' \
                %s
                """;

    public Keycloak(EnvironmentOperator testEnvironmentOperator, KubeClient kubeClient, String namespace) {
        this.testEnvironmentOperator = testEnvironmentOperator;
        this.kubeClient = kubeClient;
        this.namespace = namespace;
        this.keycloakVersion = testEnvironmentOperator.getKeycloakVersion();
    }

    public void deployOperator() {
        setupKeycloakOperator();
        deployPostgres();
        applyKeycloakResources();
        setupAdminLogin();
    }

    public void undeployOperator() {
        kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).delete();
        if (deployRealmFilePath != null) {
            TestUtils.deleteFile(Paths.get(deployRealmFilePath));
        }
        LOGGER.info("[{}] [KC] Successfully undeployed Keycloak.", namespace);
        postgres.undeployPostgres();
    }

    protected void deployPostgres() {
        postgres = ResourceManager.getPostgresInstance(namespace);
        postgres.deployPostgres("keycloak");
    }

    protected void applyKeycloakResources() {
        // Create self-signed certificate
        Secret routerSecret = kubeClient.getRouterDefaultSecret(); // or generate self-signed cert when on nonOCP
        kubeClient.createSecretEncodedData(namespace, routerSecret.getMetadata().getName(),
                Map.of(
                        "tls.crt", routerSecret.getData().get("tls.crt"),
                        "tls.key", routerSecret.getData().get("tls.key")
                ),
                true
        );

        String keycloakCr = String.format("""
            apiVersion: k8s.keycloak.org/v2alpha1
            kind: Keycloak
            metadata:
              name: keycloak
            spec:
              instances: 1
              additionalOptions:
                - name: log-level
                  value: info
                - name: http-enabled
                  value: "true"
              db:
                vendor: postgres
                host: %s
                usernameSecret:
                  name: %s
                  key: username
                passwordSecret:
                  name: %s
                  key: password
              http:
                tlsSecret: %s
              hostname:
                strict: false
            """, postgres.getName(), postgres.getSecretName(), postgres.getSecretName(), routerSecret.getMetadata().getName());

        HasMetadata keycloak = kubeClient.getKubernetesClient().resource(keycloakCr).inNamespace(namespace).createOrReplace();
        keycloakResources.add(keycloak);
        waitForKeycloakDeployment("keycloak-0", "postgresdb-0");
        if (kubeClient.isOpenshiftPlatform()) {
            kubeClient.createRoute(namespace, "keycloak", "8443", kubeClient.getServiceByName(namespace, "keycloak-service"));
        }
    }

    private void setupKeycloakOperator() {
        String mainKeycloakUrl = "https://raw.githubusercontent.com/keycloak/keycloak-k8s-resources/" + keycloakVersion;
        try {
            kubeClient.listPods(namespace);
            String kcVersion = testEnvironmentOperator.getKeycloakVersion();
            LOGGER.info("[{}] [KC] Deploying Keycloak {}", namespace, kcVersion);

            // Load and create Keycloak resources
//            String platform = kubeClient.isOpenshiftPlatform() ? "openshift" : "kubernetes";
            String platform = "kubernetes";
            URL keycloakCrdUrl = new URL(String.format("%s/kubernetes/keycloaks.k8s.keycloak.org-v1.yml", mainKeycloakUrl));
            URL keycloakRealmImportsCrdUrl = new URL(String.format("%s/kubernetes/keycloakrealmimports.k8s.keycloak.org-v1.yml", mainKeycloakUrl));
            URL kcDeploymentFileUrl = new URL(String.format("%s/kubernetes/%s.yml", mainKeycloakUrl, platform));
            List<URL> urls = List.of(keycloakCrdUrl, keycloakRealmImportsCrdUrl, kcDeploymentFileUrl);
            for (URL url : urls) {
                // Load Yaml into Kubernetes resources
                LOGGER.debug("[{}] [KC] Loading and creating keycloak resource/s from \n{}", namespace, url);
                keycloakResources.addAll(kubeClient.getKubernetesClient().load(url.openStream()).items());
            }
            // Apply Kubernetes Resources
            kubeClient.getKubernetesClient().resourceList(keycloakResources).inNamespace(namespace).createOrReplace();

            // Wait for ReplicaSet readiness
            TestUtils.waitFor("Keycloak Operator ReplicaSet to be ready", Constants.DURATION_5_SECONDS, Constants.DURATION_5_MINUTES, () -> {
                List<ReplicaSet> rSets = kubeClient.getReplicaSetsWithPrefix(namespace, "keycloak-operator");
                return rSets != null && rSets.size() > 0 &&
                        rSets.get(0).getStatus().getReadyReplicas().equals(rSets.get(0).getSpec().getReplicas());
            });
            LOGGER.info("[{}] [KC] Successfully deployed Keycloak Operator.", namespace);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        DataStorer.dumpResourceToFile(keycloakResources);
    }

    protected void waitForKeycloakDeployment(String keycloakPodName, String keycloakDbPodName) {
        TestUtils.waitFor("[KC] Keycloak pods to show up", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () ->
                kubeClient.getFirstPodByPrefixName(namespace, keycloakPodName) != null &&
                        kubeClient.getFirstPodByPrefixName(namespace, keycloakDbPodName) != null);

        keycloakPod = kubeClient.getFirstPodByPrefixName(namespace, keycloakPodName);
        keycloakSqlPod = kubeClient.getFirstPodByPrefixName(namespace, keycloakDbPodName);
        LOGGER.info("[{}] Waiting for pods {} and {} to be ready", namespace, keycloakPod.getMetadata().getName(), keycloakSqlPod.getMetadata().getName());

        kubeClient.getKubernetesClient().pods().inNamespace(namespace).resource(keycloakSqlPod).waitUntilReady(5, TimeUnit.MINUTES);
        try {
            kubeClient.getKubernetesClient().pods().inNamespace(namespace).resource(keycloakPod).waitUntilReady(3, TimeUnit.MINUTES);
        } catch (KubernetesClientTimeoutException e) {
            LOGGER.debug("[{}] [KC] keycloak {} failed to start due to internal DB issue. Restarting.", namespace, keycloakPodName);
            restartStuckKeycloakPod(e);
        }
        LOGGER.info("[{}] Keycloak pods are ready", namespace);
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

    protected void setupAdminLogin() {
        String kcSecretName = "keycloak-initial-admin";
        String adminUsernameKey = "username";
        String adminPasswordKey = "password";
        Map<String, String> adminPassword = kubeClient.getSecretByPrefixName(namespace, kcSecretName).get(0).getData();
        admin.put(adminUsernameKey, TestUtils.getDecodedBase64String(adminPassword.get(adminUsernameKey)));
        admin.put(adminPasswordKey, TestUtils.getDecodedBase64String(adminPassword.get(adminPasswordKey)));

        LOGGER.info("[{}] [KC] Using login credentials {}/{}", namespace, admin.get(adminUsernameKey), admin.get(adminPasswordKey));
        String loginCommand = String.format("%s config credentials --server http://localhost:8080 --realm master --user %s --password %s %s",
                getKcadmCmd(), admin.get(adminUsernameKey), admin.get(adminPasswordKey), tmpConfig);
        keycloakPod = kubeClient.getFirstPodByPrefixName(namespace, "keycloak-0");
        executeKeycloakCmd(loginCommand);
    }
    public void importRealm(String realmName, String realmFilePath) {
        LOGGER.debug("[{}] [KC] Importing realm {} from file {}", namespace, realmName, realmFilePath);
        String realmPodFilePath = "/tmp/" + realmName + "_realm.json";
        kubeClient.uploadFileToPod(namespace, keycloakPod, realmFilePath, realmPodFilePath);

        String createImportRealm = String.format("%s create realms -s realm=%s -s enabled=true %s && " +
                "%s create partialImport -r %s -s ifResourceExists=SKIP -o -f %s %s", getKcadmCmd(), realmName, tmpConfig, getKcadmCmd(), realmName, realmPodFilePath, tmpConfig);
        executeKeycloakCmd(createImportRealm);
    }

    public void setupLdapModule(String realmName) {
        LOGGER.info("[{}] [KC] Setup LDAP Modules in realm {}", namespace, realmName);
        String kcAdmParentId = String.format("%s get realms/%s --fields id --format csv --noquotes %s", getKcadmCmd(), realmName, tmpConfig);
        String parentId = executeKeycloakCmd(kcAdmParentId).replace("\n", "");

        String result = executeKeycloakCmd(String.format(kcadmCreateLdap, getKcadmCmd(), "realms/" + realmName + "/components", parentId, tmpConfig));
        String ldapId = result.substring(result.indexOf("'") + 1, result.lastIndexOf("'"));

        LOGGER.info("[{}] [KC] Create LDAP role mapper into realm {}", namespace, realmName);
        executeKeycloakCmd(String.format(kcAdmCreateLdapRoleMapper, getKcadmCmd(), realmName, ldapId, tmpConfig));

        LOGGER.info("[{}] [KC] Import LDAP users into realm {}", namespace, realmName);
        String syncUsersCmd = String.format("%s create realms/%s/user-storage/%s/sync?action=triggerFullSync %s", getKcadmCmd(), realmName, ldapId, tmpConfig);
        executeKeycloakCmd(syncUsersCmd);
    }

    public void setupRedirectUris(String realm, String clientName, ActiveMQArtemis broker) {
        // Update redirectUris with current webconsole routes (ex-aao-wconsj-0-svc-rte-oauth-tests)
        String clientId = getClientId(realm, clientName);
        LOGGER.debug("[{}] [KC] Update redirectUris in realm {} {}", namespace, realm, clientName);
        List<String> uris = kubeClient.getExternalAccessServiceUrlPrefixName(
                namespace, broker.getMetadata().getName() + "-" + ArtemisConstants.WEBCONSOLE_URI_PREFIX + "-");
        String format = broker.getSpec().getConsole().getSslEnabled() ? "https://ROUTE/console/*" : "http://ROUTE/console/*";

        StringBuilder constructRoutes = new StringBuilder();
        for (String uri : uris) {
            constructRoutes.append("\"").append(format.replaceAll("ROUTE", uri)).append("\", ");
        }
        constructRoutes.deleteCharAt(constructRoutes.lastIndexOf(","));

        LOGGER.info("[{}] [KC] Constructed routes\n{}", namespace, constructRoutes);
        String updateRedirectUris = String.format("%s update realms/%s/clients/%s/ -s 'redirectUris=[%s]' %s ", getKcadmCmd(),  realm, clientId, constructRoutes, tmpConfig);
        executeKeycloakCmd(updateRedirectUris);
    }

    public String getClientSecretId(String realm, String clientName) {
        // https://www.keycloak.org/docs/16.1/securing_apps/#client-id-and-client-secret
        String clientId = getClientId(realm, clientName);
        String clientSecretCmd = String.format("%s get realms/%s/clients/%s/client-secret %s | grep value | tr -d ' \",' | cut -d ':' -f 2",
                getKcadmCmd(), realm, clientId, tmpConfig);
        String clientSecretId = executeKeycloakCmd(clientSecretCmd);
        if (clientSecretId.equals("**********")) {
            LOGGER.debug("[{}] [KC] Generate new {} client-secret as it is empty", namespace, clientName);
            String clientGenerateSecretCmd = String.format("%s create realms/%s/clients/%s/client-secret %s", getKcadmCmd(), realm, clientId, tmpConfig);
            executeKeycloakCmd(clientGenerateSecretCmd);
            clientSecretId = executeKeycloakCmd(clientSecretCmd);
        }
        LOGGER.debug("[{}] [KC] Using {} client-secret {}", namespace, realm, clientSecretId);
        return clientSecretId;
    }

    protected String getKcadmCmd() {
        return kcadmCmd;
    }

    private String getClientId(String realm, String clientName) {
        String keycloakClientIdCmd = String.format("%s get realms/%s/clients?clientId=%s %s | grep '\"id\"' | tr -d ' \",' | cut -d ':' -f 2",
                getKcadmCmd(), realm, clientName, tmpConfig);
        String clientId = executeKeycloakCmd(keycloakClientIdCmd);
        LOGGER.debug("[{}] [KC] Found clientId {} for realm {} ", namespace, clientId, realm);
        return clientId;
    }

    public String getAuthUri() {
        return "https://" + kubeClient.getExternalAccessServiceUrl(namespace, "keycloak");
    }

    String executeKeycloakCmd(String keycloakCommand) {
        String output = kubeClient.executeCommandInPod(keycloakPod, keycloakCommand, Constants.DURATION_10_SECONDS).strip();
        LOGGER.debug("[KC] {}", output);
        // if error execute login() again, due to workaround using config in tmp - possible tmp-cleanup removal
        return output;
    }

    String getJwtToken(String realm, String clientId, String username, String password) {
        try {
            String tokenUrl = String.format(tokenUrlTemplate, getAuthUri(), realm);
            URLConnection connection = TestUtils.makeInsecureHttpsRequest(tokenUrl);
            HttpURLConnection http = (HttpURLConnection) connection;
            http.setRequestMethod(Constants.POST);
            http.setDoOutput(true);
//            http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            Map<String, String> arguments = new HashMap<>();
            arguments.put("username", username);
            arguments.put("password", password);
            arguments.put("grant_type", "password");
            arguments.put("client_id", clientId);

            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : arguments.entrySet())
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                    .append("&");
            String postData = sb.substring(0, sb.length() - 1);
            DataOutputStream out = new DataOutputStream(http.getOutputStream());
            out.writeBytes(postData);
            out.flush();
            out.close();
            http.disconnect();

            InputStream response = http.getInputStream();
            Scanner scanner = new Scanner(response);
            String responseBody = scanner.useDelimiter("\\A").next();
            LOGGER.trace(responseBody);
            response.close();
            JSONObject responseJson = new JSONObject(responseBody);
            return responseJson.getString("access_token");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

//    protected void login() {
//        String loginCommand = String.format("%s config credentials --server http://localhost:8080 --realm master --user %s --password %s %s",
//                getKcadmCmd(), admin.get(adminUsernameKey), admin.get(adminPasswordKey), tmpConfig);
//        executeKeycloakCmd(loginCommand);
//    }

}
