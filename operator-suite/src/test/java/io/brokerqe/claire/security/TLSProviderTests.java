/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class TLSProviderTests extends AbstractSystemTests {

    private static final String JDK_PROVIDER = "JDK";
    private static final String OPENSSL_PROVIDER = "OPENSSL";
    private static final String INVALID_PROVIDER = "INVALID_PROVIDER";
    private static final String EMPTY_PROVIDER = "";
    private static final String SPACES_PROVIDER = "     ";

    private static final Logger LOGGER = LoggerFactory.getLogger(TLSProviderTests.class);
    private final String testNamespace = getRandomNamespaceName("tls-provider-tests", 3);
    private final String brokerSecretPrefix = "broker-tls-secret-";
    private final String clientSecretPrefix = "client-tls-secret-";

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @AfterEach
    void cleanEnvironment() {
        cleanResourcesAfterTest(testNamespace);

        List<Secret> secrets = getClient().getSecretByPrefixName(testNamespace, brokerSecretPrefix);
        secrets.addAll(getClient().getSecretByPrefixName(testNamespace, clientSecretPrefix));
        for (Secret secret : secrets) {
            getClient().deleteSecret(secret);
        }
    }

    static Stream<Arguments> supportedProviders() {
        Stream<Arguments> stream = Stream.of(
                Arguments.of(Named.of("Default sslProvider", null)),
                Arguments.of(Named.of(JDK_PROVIDER + " provider", JDK_PROVIDER)),
//                Arguments.of(Named.of(OPENSSL_PROVIDER + " provider", OPENSSL_PROVIDER)),
                Arguments.of(Named.of(INVALID_PROVIDER + " provider", INVALID_PROVIDER)),
                Arguments.of(Named.of("Empty provider", EMPTY_PROVIDER)),
                Arguments.of(Named.of("Spaces only provider", SPACES_PROVIDER))
        );
        if (ResourceManager.getEnvironment().getArtemisTestVersion().getVersionNumber() >= ArtemisVersion.VERSION_2_28.getVersionNumber()) {
            // OPENSSL Provider does not work on 7.10.x https://issues.redhat.com/browse/ENTMQBR-8294
            stream = Stream.concat(stream, Stream.of(Arguments.of(Named.of(OPENSSL_PROVIDER + " provider", OPENSSL_PROVIDER))));
        }
        return stream;
    }

    @ParameterizedTest
    @MethodSource("supportedProviders")
    void testTLSProviders(String sslProvider) {
        LOGGER.info("Starting to test TLS Provider: {}", sslProvider);
        String brokerSecretName = "broker-tls-secret-" + TestUtils.getRandomString(3);
        String clientSecret = "client-tls-secret-" + TestUtils.getRandomString(3);
        String amqpAcceptorName = "amqp-" + TestUtils.getRandomString(3);
        String brokerName = "broker-" + TestUtils.getRandomString(3);

        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true,
                brokerSecretName, true, sslProvider);
        BPActiveMQArtemisAddress tlsAddress = ResourceManager.createBPArtemisAddress(ArtemisConstants.ROUTING_TYPE_ANYCAST);

        ActiveMQArtemis broker = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(brokerName)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                    .endDeploymentPlan()
                    .withAcceptors(amqpAcceptors)
                    .withBrokerProperties(tlsAddress.getPropertiesList())
                .endSpec()
                .build();

        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                ResourceManager.generateDefaultClientDN(),
                List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName))),
                null
        );

        // One Way TLS
        getClient().createSecretEncodedData(testNamespace, brokerSecretName, CertificateManager.createBrokerKeystoreSecret(keystores));

        // Two Way - Mutual Authentication (Clients TLS secret)
        getClient().createSecretEncodedData(testNamespace, clientSecret, CertificateManager.createClientKeystoreSecret(keystores));

        broker = ResourceManager.createArtemis(testNamespace, broker);
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        LOGGER.info("[{}] Broker {} is up and running with TLS", testNamespace, brokerName);

        assertBrokerConfigHasSslProvider(brokerPod, sslProvider);
        Deployment clients = ResourceManager.deploySecuredClientsContainer(testNamespace, List.of(clientSecret));
        Pod clientsPod = getClient().getFirstPodByPrefixName(testNamespace, Constants.PREFIX_SYSTEMTESTS_CLIENTS);
        testTlsMessaging(testNamespace, tlsAddress, brokerUris.get(0), null, clientSecret, clientsPod,
                Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());
        ResourceManager.undeployClientsContainer(testNamespace, clients);
        getClient().deletePod(testNamespace, clientsPod);
        ResourceManager.deleteArtemis(testNamespace, broker);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        getClient().deleteSecret(testNamespace, clientSecret);
    }

    private void assertBrokerConfigHasSslProvider(Pod artemisPod, String sslProvider) {
        String cmd = "grep \"acceptor name=\" " + ArtemisConstants.CONTAINER_BROKER_HOME_ETC_DIR + "broker.xml";
        String acceptors = getClient().executeCommandInPod(artemisPod, cmd, Constants.DURATION_10_SECONDS);

        if (sslProvider == null || sslProvider.isEmpty()) {
            LOGGER.info("[{}] Ensure artemis pod has the broker.xml with acceptor without sslProvider", testNamespace);
            assertThat(acceptors).doesNotContain("sslProvider=");
        } else {
            if (sslProvider.isBlank()) {
                LOGGER.info("[{}] Ensure artemis pod has the broker.xml with acceptor with sslProvider is blank", testNamespace);
            } else {
                LOGGER.info("[{}] Ensure artemis pod has the broker.xml with acceptor with sslProvider={}", testNamespace, sslProvider);
            }
            assertThat(acceptors).contains("sslProvider=" + sslProvider);
        }

    }

}
