/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.KubernetesArchitecture;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.DisabledTestArchitecture;
import io.brokerqe.claire.junit.TestValidSince;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@TestValidSince(ArtemisVersion.VERSION_2_28)
@DisabledTestArchitecture(archs = {KubernetesArchitecture.S390X, KubernetesArchitecture.ARM64, KubernetesArchitecture.PPC64LE})
public class KeycloakLdapTests extends LdapTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeycloakLdapTests.class);
    private final String testNamespace = getRandomNamespaceName("kc-ldap-tests", 3);
    private static boolean isOidcEnabled = false;
    private Keycloak keycloak;
    private Openldap openldap;
    String keycloakRealm = "amq-broker-ldap";
    String brokerName = "artemis";
    String amqpAcceptorName = "my-amqp";
    String secretConfigName = "keycloak-jaas-config";
    String hawtioOidcConfigName = "hawtio-oidc-config";
    String debugLoggingConfigName = "debug-logging-config";
    String consoleSecretName = brokerName + "-console-secret";
    String brokerTruststoreSecretName = "broker-truststore";
    final boolean jwtTokenSupported = true;
    List<String> secretMounts = new ArrayList<>(List.of(brokerTruststoreSecretName, secretConfigName));
    List<String> configMapMounts = new ArrayList<>(List.of(debugLoggingConfigName));

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        openldap = ResourceManager.getOpenldapInstance(testNamespace);
        openldap.deployLdap();
        keycloak = ResourceManager.getKeycloakInstance(testNamespace);
        keycloak.deploy();
        setupEnvironment();
    }

    @AfterAll
    void teardownClusterOperator() {
        ResourceManager.deleteArtemis(testNamespace, broker);
        if (isOidcEnabled) {
            getClient().deleteConfigMap(testNamespace, hawtioOidcConfigName);
        } else {
            getClient().deleteConfigMap(testNamespace, secretConfigName);
        }
        keycloak.undeploy();
        openldap.undeployLdap();
        teardownDefaultClusterOperator(testNamespace);
    }

    String getTestNamespace() {
        return testNamespace;
    }

    void createTemplateHawtioOidcFile(String keycloakAuthUri) {
        LOGGER.info("[{}] Creating custom hawtio-oidc.properties config file.", testNamespace);
        // pre-create webconsole URL, cause broker is not yet deployed
        String webUrl = keycloakAuthUri.replace("keycloak", "artemis-wconsj-0") + "/console/*";

        String template = TestUtils.readFileContent(Path.of(Constants.HAWTIO_OIDC_TEMPLATE).toFile());
        String templatedFile = template
                .replace("BROKER_NAME", brokerName)
                .replace("TEST_NAMESPACE", testNamespace)
                // https://artemis-wconsj-0-svc-rte-kc-ldap-tests.apps.<cluster-name>.clusters.amq-broker.xyz/console/*
                .replace("REDIRECT_URI", webUrl)
                // https://keycloak-svc-rte-kc-ldap-tests.apps.<cluster-name>.clusters.amq-broker.xyz/realms/amq-broker-ldap/
                .replace("PROVIDER_URL", keycloakAuthUri + "/realms/amq-broker-ldap/");
        getClient().createConfigMap(testNamespace, hawtioOidcConfigName, Map.of("hawtio-oidc.properties", templatedFile));
    }

    public String createArtemisKeycloakSecurity() {
        String keycloakAuthUri = keycloak.getAuthUri();
        String clientSecretId = keycloak.getClientSecretId(keycloakRealm, "amq-broker");
        isOidcEnabled = ResourceManager.getEnvironment().getArtemisTestVersion().getVersionNumber() >= ArtemisVersion.VERSION_2_40.getVersionNumber();
        String hawtioKeycloakOptions;
        String consoleRealmJAAS = "";
        Map<String, String> jaasData = new HashMap<>();

        // Currently only Hawtio OIDC for console JAAS realm is enabled.
        // For messaging realm as well see https://issues.redhat.com/browse/ENTMQBR-1828
        if (isOidcEnabled) {
            LOGGER.info("[{}] Using Hawtio OpenIdConnect", testNamespace);
            createTemplateHawtioOidcFile(keycloakAuthUri);
            hawtioKeycloakOptions = " -Dhawtio.oidcConfig=/amq/extra/configmaps/" + hawtioOidcConfigName + "/hawtio-oidc.properties"
                    // following should be hopefully not needed once ENTMQBR-1828 is implemented as well
                    + " -Dsecret.mount=/amq/extra/secrets/" + secretConfigName
                    + " -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal"
                    + " -Dhawtio.keycloakEnabled=true -Dhawtio.keycloakClientConfig=/amq/extra/secrets/" + secretConfigName + "/_keycloak-js-client.json";
            configMapMounts.add(hawtioOidcConfigName);

        } else {
            LOGGER.info("[{}] Using old JAAS settings", testNamespace);
            // Hawtio BearerToken authentication
            hawtioKeycloakOptions = " -Dsecret.mount=/amq/extra/secrets/" + secretConfigName
                    + " -Dhawtio.rolePrincipalClasses=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal"
                    + " -Dhawtio.keycloakEnabled=true -Dhawtio.keycloakClientConfig=/amq/extra/secrets/" + secretConfigName + "/_keycloak-js-client.json";
            consoleRealmJAAS = """
                    console {
                        // ensure the operator can connect to the broker by referencing the existing properties config
                        org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
                            org.apache.activemq.jaas.properties.user="artemis-users.properties"
                            org.apache.activemq.jaas.properties.role="artemis-roles.properties"
                            baseDir="/home/jboss/amq-broker/etc";

                       org.keycloak.adapters.jaas.BearerTokenLoginModule optional
                            keycloak-config-file="${secret.mount}/_keycloak-bearer-token.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
                    };
                    """;
            jaasData.put("_keycloak-js-client.json", String.format("""
                    {
                      "realm": "%s",
                      "clientId": "amq-console",
                      "url": "%s"
                    }""", keycloakRealm, keycloakAuthUri));
        }
        // Create JAAS data with proper modules & login methods
        jaasData.put(ArtemisConstants.LOGIN_CONFIG_CONFIG_KEY, consoleRealmJAAS + """
                    activemq {
                        org.keycloak.adapters.jaas.BearerTokenLoginModule optional
                            keycloak-config-file="${secret.mount}/_keycloak-bearer-token.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

                        org.keycloak.adapters.jaas.DirectAccessGrantsLoginModule optional
                            keycloak-config-file="${secret.mount}/_keycloak-direct-access.json"
                            role-principal-class=org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;

                        org.apache.activemq.artemis.spi.core.security.jaas.PrincipalConversionLoginModule required
                           principalClassList=org.keycloak.KeycloakPrincipal;
                    };
                    """);
        jaasData.put("_keycloak-direct-access.json", String.format("""
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
                """, keycloakRealm, keycloakAuthUri, clientSecretId));
        jaasData.put("_keycloak-bearer-token.json", String.format("""
                    {
                        "realm": "%s",
                        "resource": "amq-console",
                        "auth-server-url": "%s",
                        "principal-attribute": "preferred_username",
                        "use-resource-role-mappings": false,
                        "ssl-required": "external",
                        "confidential-port": 0
                    }
                    """, keycloakRealm, keycloakAuthUri));

        // create automagically mounted secret keycloak-jaas-config
        LOGGER.debug("[{}] Creating JAAS config with data {}", testNamespace, jaasData);
        getClient().createSecretStringData(testNamespace, secretConfigName, jaasData, true);
        return hawtioKeycloakOptions;
    }

    public void setupEnvironment() {
        String brokerTruststoreFileName = CertificateManager.getCurrentTestDirectory() + "brk_truststore.jks";
        KubeClient.createBrokerTruststoreSecretWithOpenshiftRouter(getClient(), testNamespace, brokerTruststoreSecretName, brokerTruststoreFileName);
        keycloak.importRealm(keycloakRealm, keycloak.realmArtemisLdap);
        keycloak.setupLdapModule(keycloakRealm);

        String hawtioKeycloakOptions = createArtemisKeycloakSecurity();

        getClient().createConfigMap(testNamespace, debugLoggingConfigName,
                Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, """
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
        ldapAddress = ResourceManager.createBPArtemisAddress(ArtemisConstants.ROUTING_TYPE_ANYCAST);

        broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .addToEnv(new EnvBuilder()
                    .withName("JAVA_ARGS_APPEND")
                    .withValue(
                            // Broker Truststore with Openshift Route certificate
                            "-Djavax.net.ssl.trustStore=/amq/extra/secrets/" + brokerTruststoreSecretName + "/" + Constants.BROKER_TRUSTSTORE_ID
                            + " -Djavax.net.ssl.trustStorePassword=" + CertificateManager.DEFAULT_BROKER_PASSWORD + " -Djavax.net.ssl.trustStoreType=jks"
                            + " -Dwebconfig.bindings.artemis.sniRequired=false"
                            + " -Dwebconfig.bindings.artemis.sniHostCheck=false"
                            + " -Dhawtio.authenticationEnabled=true"
                            + " -Dhawtio.realm=console"
                            + hawtioKeycloakOptions
                    ).build()
                )
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                    .withJolokiaAgentEnabled(true)
                    .withManagementRBACEnabled(true)
                    .editOrNewExtraMounts()
                        .withSecrets(secretMounts)
                        .withConfigMaps(configMapMounts)
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
        broker.getSpec().getBrokerProperties().addAll(ldapAddress.getPropertiesList());
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                ResourceManager.generateDefaultClientDN(),
                List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName, ArtemisConstants.WEBCONSOLE_URI_PREFIX))),
                null
        );
        getClient().createSecretEncodedData(testNamespace, consoleSecretName, CertificateManager.createConsoleKeystoreSecret(keystores));
        broker = ResourceManager.createArtemis(testNamespace, broker);
        keycloak.setupRedirectUris(keycloakRealm, "amq-console", broker);

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
