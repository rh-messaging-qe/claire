/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurityBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.KeycloakLoginModules;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.KeycloakLoginModulesBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.PropertiesLoginModulesBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.loginmodules.keycloakloginmodules.configuration.CredentialsBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.BrokerBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.management.authorisation.AllowedListBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.amq.broker.v1beta1.activemqartemisspec.EnvBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.deploymentplan.ExtraMountsBuilder;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.TestUtils;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Disabled("Keycloak integration not ready yet")
public class OauthSecurityTests extends AbstractSystemTests {
    /* Keycloak tests */

    private static final Logger LOGGER = LoggerFactory.getLogger(OauthSecurityTests.class);
    private final String testNamespace = getRandomNamespaceName("oauth-tests", 6);
    private Keycloak keycloak;


    @BeforeAll
    void setupClusterOperator() {
        getClient().createNamespace(testNamespace, true);
        setupDefaultClusterOperator(testNamespace);
        keycloak = ResourceManager.getKeycloakInstance(testNamespace);
        keycloak.deployOperator();
    }

    @AfterAll
    void teardownClusterOperator() {
        keycloak.undeployOperator();
        teardownDefaultClusterOperator(testNamespace);
        getClient().deleteNamespace(testNamespace);
    }

    public ActiveMQArtemisSecurity createArtemisSecurity() {

        //TODO: Jaas is deprecated?!
        List<KeycloakLoginModules> kcLoginModules = List.of(new KeycloakLoginModulesBuilder()
                .withName("login-keycloak-broker-module")
                .withModuleType("directAccess")
                .editOrNewConfiguration()
                    .withRealm("amq-broker-realm")
                    .withResource("amq-broker")
                    .withAuthServerUrl("https://keycloak-oauth-tests.apps.mtoth-412p.broker.app-services-dev.net/auth/")
                    .withEnableCors(false)
                .withPrincipalAttribute("preferred_username")
                .withUseResourceRoleMappings(true)
                .withVerifyTokenAudience(true)
                .withSslRequired("all")
                .withConfidentialPort(0)
                .withEnableBasicAuth(true)
                .withCredentials(new CredentialsBuilder()
                        .withKey("secret")
                        .withValue("amq-broker-secret-value").build())
                .endV1beta1Configuration().build(),

                new KeycloakLoginModulesBuilder()
                .withName("login-keycloak-console-module")
                .withModuleType("bearerToken")
                .editOrNewConfiguration()
                    .withRealm("amq-broker-realm")
                    .withResource("amq-console")
                    .withPublicClient(true)
                    .withAuthServerUrl("https://keycloak-oauth-tests.apps.mtoth-412p.broker.app-services-dev.net/auth/")
                    .withSslRequired("all")
                    .withPrincipalAttribute("preferred_username")
                    .withUseResourceRoleMappings(true)
                    .withVerifyTokenAudience(false)
                    .withConfidentialPort(0)
                    .withEnableBasicAuth(true)
                .endV1beta1Configuration()
                .build()
        );
        // DEPLOY 70-amq-broker-keycloak-module-security_0.yml
        ActiveMQArtemisSecurity artemisSecurity = new ActiveMQArtemisSecurityBuilder()
                .editOrNewMetadata()
                    .withName("keycloak-module-security")
                .endMetadata()
                .editOrNewSpec()
//                    .withApplyToCrNames(brokerName)
                    .editOrNewLoginModules()
                        .withKeycloakLoginModules(kcLoginModules)
                        .withPropertiesLoginModules(new PropertiesLoginModulesBuilder().withName("properties-module").build())
                    .endV1beta1LoginModules()
                    .editOrNewSecurityDomains()
                        .editOrNewBrokerDomain()
                            .withName("activemq")
                            .addNewBrokerdomainLoginModule()
                                .withName("login-keycloak-broker-module")
                                .withFlag("required")
                                .withDebug(true)
                            .endBrokerdomainLoginModule()
                        .endV1beta1BrokerDomain()

                        .editOrNewConsoleDomain()
                            .withName("console")
                            .addNewConsoledomainLoginModule()
                                .withName("login-keycloak-console-module")
                                .withFlag("sufficient")
                                .withDebug(true)
                            .endConsoledomainLoginModule()
                            .and()
                            .editOrNewConsoleDomain()
                            .addNewConsoledomainLoginModule()
                                .withName("properties-module")
                                .withFlag("required")
                                .withDebug(true)
                            .endConsoledomainLoginModule()
                        .endV1beta1ConsoleDomain()
                    .endV1beta1SecurityDomains()
                    .editOrNewSecuritySettings()
                        .addToBroker(new BrokerBuilder().withMatch("#").withPermissions(allAdminPermissions).build())
                        .addToBroker(new BrokerBuilder().withMatch("activemq.management.#").withPermissions(activemqManagementAllAdminPermissions).build())
                        .editOrNewManagement()
                            .withHawtioRoles("admin", "viewer")
                            .editOrNewAuthorisation()
                                .withAllowedList(new AllowedListBuilder().withDomain("hawtio").build())
                                .withRoleAccess(roleAccess)
                            .endV1beta1Authorisation()
                        .endV1beta1Management()
                    .endV1beta1SecuritySettings()
                .endSpec()
                .build();
        return artemisSecurity;
    }
    @Test
    @Tag("jaas")
    public void keycloakDeploymentTest() {
        String brokerName = "artemis";
        String amqpAcceptorName = "my-amqp";
        String brokerSecretName = "broker-tls-secret";
        String clientSecretName = "client-tls-secret";
        String consoleSecretName = brokerName + "-console-secret";
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName, false);
        ActiveMQArtemisAddress tlsAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());

        ActiveMQArtemis artemis = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                        .withJolokiaAgentEnabled(true)
                        .withManagementRBACEnabled(true)
                    .endDeploymentPlan()
                    .withAcceptors(List.of(amqpAcceptors))
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(true)
//                        .withUseClientAuth()
                    .endConsole()
                .endSpec()
                .build();
//        Map<String, KeyStoreData> keystores = CertificateManager.reuseDefaultGeneratedKeystoresFromFiles();
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                testNamespace,
                artemis,
                CertificateManager.generateDefaultBrokerDN(getKubernetesClient()),
                CertificateManager.generateDefaultClientDN(getKubernetesClient()),
                List.of(CertificateManager.generateSanDnsNames(getClient(), artemis, List.of(amqpAcceptorName, Constants.WEBCONSOLE_URI_PREFIX))),
                null
        );

        // https://access.redhat.com/solutions/6973839 Get route secret
        KeyStoreData truststoreBrokerData = keystores.get(Constants.BROKER_TRUSTSTORE_ID);
        Secret routeSecret = getKubernetesClient().secrets().inNamespace("openshift-ingress").withName("router-certs-default").get();
        String routeAlias =  "*." + testNamespace + "." + getKubernetesClient().getMasterUrl().getHost().replace("api", "apps");
        CertificateManager.addToTruststore(truststoreBrokerData, routeSecret.getData().get("tls.crt"), routeAlias);
//        CertificateManager.addToTruststore(keystores.get(Constants.CLIENT_TRUSTSTORE_ID), routeSecret.getData().get("tls.crt"), routeAlias);

        Secret brokerSecret = CertificateManager.createBrokerKeystoreSecret(getClient(), brokerSecretName, keystores);
        Secret clientSecret = CertificateManager.createClientKeystoreSecret(getClient(), clientSecretName, keystores);
        Secret consoleSecret = CertificateManager.createConsoleKeystoreSecret(getClient(), consoleSecretName, keystores);

        ConfigMap cm = new ConfigMapBuilder()
                .editOrNewMetadata()
                .withName("keycloak-truststore")
                .endMetadata()
                .withBinaryData(Map.of("brokerUser_keystore.jks", truststoreBrokerData.getEncodedKeystoreFileData()))
                .build();
        getKubernetesClient().configMaps().inNamespace(testNamespace).resource(cm).createOrReplace();

        artemis.getSpec().setEnv(List.of(
                new EnvBuilder()
                    .withName("DEBUG_ARGS")
                    .withValue("-Djavax.net.ssl.trustStore=/amq/extra/configmaps/keycloak-truststore/brokerUser_keystore.jks -Djavax.net.ssl.trustStorePassword=brokerPass -Djavax.net.ssl.trustStoreType=jks")
                .build())
        );
        artemis.getSpec().getDeploymentPlan().setExtraMounts(
                new ExtraMountsBuilder()
                        .withConfigMaps("keycloak-truststore")
                .build());

        // DEPLOY 70-amq-broker-keycloak-module-security_0.yml
        ActiveMQArtemisSecurity security = ResourceManager.createArtemisSecurity(testNamespace, createArtemisSecurity());
        artemis = ResourceManager.createArtemis(testNamespace, artemis, true);
        int brokerConsoles = artemis.getSpec().getDeploymentPlan().getSize();
        TestUtils.waitFor("webconsole external uri availability", Constants.DURATION_5_SECONDS, Constants.DURATION_3_MINUTES, () -> {
            return getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + Constants.WEBCONSOLE_URI_PREFIX + "-").size() == brokerConsoles;
        });
        keycloak.importRealm("amq-broker-realm", keycloak.realmArtemis, artemis.getMetadata().getName());
        LOGGER.info("[{}] Starting KC test. Deployed pods {}", testNamespace,
                getClient().listPods(testNamespace).stream().map(pod -> pod.getMetadata().getName()).collect(Collectors.toList()));

        Pod artemisPod0 = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);

        // Create clients and send messages
        LOGGER.info("ENV is set up properly. Deploy clients now");
        testTlsMessaging(testNamespace, artemisPod0, tlsAddress, brokerUris.get(0), null, clientSecretName,
                Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());
    }

    @Test
    @Tag("jaas")
    public void keycloakJAASExternalTest() {
        // ENTMQBR-5918 Allow to configure TextFileCertificateLoginModule
    }

}
