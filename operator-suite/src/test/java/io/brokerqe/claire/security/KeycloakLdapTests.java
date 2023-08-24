/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class KeycloakLdapTests extends LdapTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakLdapTests.class);
    private final String testNamespace = getRandomNamespaceName("kc-ldap-tests", 3);
    private Keycloak keycloak;
    private Openldap openldap;
    String keycloakRealm = "amq-broker-ldap";
    String brokerName = "artemis";
    String amqpAcceptorName = "my-amqp";
    String secretConfigName = "keycloak-jaas-config";
    String consoleSecretName = brokerName + "-console-secret";
    String brokerTruststoreSecretName = "broker-truststore";
    String brokerTruststoreFileName = Constants.CERTS_GENERATION_DIR + "brk_truststore.jks";
    final boolean jwtTokenSupported = true;

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        openldap = ResourceManager.getOpenldapInstance(testNamespace);
        openldap.deployLdap();
        keycloak = ResourceManager.getKeycloakInstance(testNamespace);
        keycloak.deployOperator();
        setupEnvironment();
    }

    @AfterAll
    void teardownClusterOperator() {
        ResourceManager.deleteArtemisAddress(testNamespace, ldapAddress);
        ResourceManager.deleteArtemis(testNamespace, broker);
        getClient().deleteConfigMap(testNamespace, secretConfigName);
        keycloak.undeployOperator();
        openldap.undeployLdap();
        teardownDefaultClusterOperator(testNamespace);
    }

    String getTestNamespace() {
        return testNamespace;
    }

    public void createArtemisKeycloakSecurity() {
        String keycloakAuthUri = keycloak.getAuthUri();
        String clientSecretId = keycloak.getClientSecretId(keycloakRealm, "amq-broker");

        Map<String, String> jaasData = Map.of(
            Constants.LOGIN_CONFIG_CONFIG_KEY, """
                    console {
                        // ensure the operator can connect to the broker by referencing the existing properties config
                        org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
                            org.apache.activemq.jaas.properties.user="artemis-users.properties"
                            org.apache.activemq.jaas.properties.role="artemis-roles.properties"
                            baseDir="/home/jboss/amq-broker/etc";
    
                       org.keycloak.adapters.jaas.BearerTokenLoginModule sufficient
                            keycloak-config-file="${secret.mount}/_keycloak-bearer-token.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
                    };
                    activemq {
                        org.keycloak.adapters.jaas.BearerTokenLoginModule sufficient
                            keycloak-config-file="${secret.mount}/_keycloak-bearer-token.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

                        org.keycloak.adapters.jaas.DirectAccessGrantsLoginModule sufficient
                            keycloak-config-file="${secret.mount}/_keycloak-direct-access.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

                        org.apache.activemq.artemis.spi.core.security.jaas.PrincipalConversionLoginModule required
                           principalClassList=org.keycloak.KeycloakPrincipal;
                    };
                    """,
            "_keycloak-bearer-token.json", String.format("""
                    {
                        "realm": "%s",
                        "resource": "amq-console",
                        "auth-server-url": "%s",
                        "principal-attribute": "preferred_username",
                        "use-resource-role-mappings": false,
                        "ssl-required": "external",
                        "confidential-port": 0
                    }
                    """, keycloakRealm, keycloakAuthUri),

            "_keycloak-direct-access.json", String.format("""
                    {
                        "realm": "%s",
                        "resource": "amq-broker",
                        "auth-server-url": "%s",
                        "principal-attribute": "preferred_username",
                        "use-resource-role-mappings": false,
                        "ssl-required": "external",
                        "credentials": {
                            "secret": "%s"
                        }
                    }
                    """, keycloakRealm, keycloakAuthUri, clientSecretId),
            "_keycloak-js-client.json", String.format("""
                    {
                      "realm": "%s",
                      "clientId": "amq-console",
                      "url": "%s"
                    }""", keycloakRealm, keycloakAuthUri)
        );
        // create automagically mounted secret keycloak-jaas-config
        LOGGER.debug("[{}] Creating JAAS config with data {}", testNamespace, jaasData);
        getClient().createSecretStringData(testNamespace, secretConfigName, jaasData, true);
    }

    public void setupEnvironment() {
        getClient().createBrokerTruststoreSecretWithOpenshiftRouter(getClient(), testNamespace, brokerTruststoreSecretName, brokerTruststoreFileName);
        keycloak.importRealm(keycloakRealm, keycloak.realmArtemisLdap);
        keycloak.setupLdapModule(keycloakRealm);
        createArtemisKeycloakSecurity();

        getClient().createConfigMap(testNamespace, "debug-logging-config",
                Map.of(Constants.LOGGING_PROPERTIES_CONFIG_KEY, """
                    appender.stdout.name = STDOUT
                    appender.stdout.type = Console
                    appender.stdout.layout.type=PatternLayout
                    appender.stdout.layout.pattern=%d %-5level [%logger](%t) %msg%n
                    rootLogger = info, STDOUT
                    logger.activemq.name=org.apache.activemq.artemis.spi.core.security.jaas
                    logger.activemq.level=debug
                    logger.activemq.netty.name=io.netty
                    logger.activemq.netty.level=info
                    logger.core.name=org.apache.activemq.artemis.core.protocol
                    logger.core.level=debug
                    logger.remoting.name=org.apache.activemq.artemis.core.remoting
                    logger.remoting.level=debug
                """)
        );

        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672);
        broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .addToEnv(new EnvBuilder()
                    .withName("JAVA_ARGS_APPEND")
                    .withValue("-Dsecret.mount=/amq/extra/secrets/" + secretConfigName
                            // Broker Truststore with Openshift Route certificate
                            + " -Djavax.net.ssl.trustStore=/amq/extra/secrets/" + brokerTruststoreSecretName + "/" + Constants.BROKER_TRUSTSTORE_ID
                            + " -Djavax.net.ssl.trustStorePassword=" + CertificateManager.DEFAULT_BROKER_PASSWORD + " -Djavax.net.ssl.trustStoreType=jks"
                            // Hawtio BearerToken authentication
                            + " -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal"
                            + " -Dhawtio.keycloakEnabled=true -Dhawtio.keycloakClientConfig=/amq/extra/secrets/" + secretConfigName + "/_keycloak-js-client.json"
                            + " -Dhawtio.authenticationEnabled=true -Dhawtio.realm=console"
                            + " -Dwebconfig.bindings.artemis.sniRequired=false"
                            + " -Dwebconfig.bindings.artemis.sniHostCheck=false"
                    )
                    .build()
                )
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                    .withJolokiaAgentEnabled(true)
                    .withManagementRBACEnabled(true)
                    .editOrNewExtraMounts()
                        .withSecrets(List.of(secretConfigName, brokerTruststoreSecretName))
                        .withConfigMaps("debug-logging-config")
                    .endExtraMounts()
                .endDeploymentPlan()
                .withBrokerProperties(List.of(
                        "securityRoles.#.producers.send=true",
                        "securityRoles.#.consumers.consume=true",
                        "securityRoles.#.producers.createAddress=true",
                        "securityRoles.#.producers.createNonDurableQueue=true",
                        "securityRoles.#.producers.createDurableQueue=true"
                ))
                .withAcceptors(List.of(amqpAcceptors))
                .editOrNewConsole()
                    .withExpose(true)
                    .withSslEnabled(true)
                    .withSslSecret(consoleSecretName)
//                        .withUseClientAuth()
                .endConsole()
            .endSpec()
            .build();
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                ResourceManager.generateDefaultClientDN(),
                List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName, Constants.WEBCONSOLE_URI_PREFIX))),
                null
        );
        getClient().createSecretEncodedData(testNamespace, consoleSecretName, CertificateManager.createConsoleKeystoreSecret(keystores));
        broker = ResourceManager.createArtemis(testNamespace, broker);
        keycloak.setupRedirectUris(keycloakRealm, "amq-console", broker);

        ldapAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        brokerPod = getClient().listPodsByPrefixName(testNamespace, brokerName).get(0);
        allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
    }

    @Override
    String getJwtToken(String username) {
        return keycloak.getJwtToken(keycloakRealm, "amq-broker", username, users.get(username));
    }

    public boolean isJwtTokenSupported() {
        return jwtTokenSupported;
    }

}
