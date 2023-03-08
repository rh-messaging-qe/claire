/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.AbstractSystemTests;
import io.brokerqe.ArtemisVersion;
import io.brokerqe.Constants;
import io.brokerqe.ResourceManager;
import io.brokerqe.junit.TestValidSince;
import io.brokerqe.junit.TestValidUntil;
import io.brokerqe.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;


public class TLSSecurityTests extends AbstractSystemTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(TLSSecurityTests.class);
    private final String testNamespace = getRandomNamespaceName("tls-tests", 6);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
    }

    @AfterAll
    void teardownClusterOperator() {
        teardownDefaultClusterOperator(testNamespace);
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    public void testMutualAuthentication() {
        doTestMutualAuthentication(true);
    }

    @Test
    @TestValidUntil(ArtemisVersion.VERSION_2_28)
    public void testMutualAuthenticationOldVersion() {
        doTestMutualAuthentication(false);
    }

    public void doTestMutualAuthentication(boolean singleSecret) {
        String brokerSecretName = "broker-tls-secret";
        String bugBrokerSecretName = brokerSecretName + "-openwire";
        String clientSecret = "client-tls-secret";
        String amqpAcceptorName = "my-amqp";
        String owireAcceptorName = "my-owire";

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "tls-broker");
        ActiveMQArtemisAddress tlsAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName, true);
        Acceptors owireAcceptors;

        if (singleSecret) {
            owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, brokerSecretName, true);
        } else {
            //  Bug must have secret for each acceptor https://issues.redhat.com/browse/ENTMQBR-4268
            owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, bugBrokerSecretName, true);
        }

//        Map<String, KeyStoreData> keystores = CertificateManager.reuseDefaultGeneratedKeystoresFromFiles();
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                testNamespace,
                broker,
                CertificateManager.generateDefaultBrokerDN(getKubernetesClient()),
                CertificateManager.generateDefaultClientDN(getKubernetesClient()),
                List.of(CertificateManager.generateSanDnsNames(getClient(), broker, List.of(amqpAcceptorName, owireAcceptorName))),
                null
        );

        // One Way TLS
        CertificateManager.createBrokerKeystoreSecret(getClient(), brokerSecretName, keystores);
        if (!singleSecret) {
            CertificateManager.createBrokerKeystoreSecret(getClient(), bugBrokerSecretName, keystores);
        }

        // Two Way - Mutual Authentication (Clients TLS secret)
        CertificateManager.createClientKeystoreSecret(getClient(), clientSecret, keystores);

        broker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors, owireAcceptors), broker);
        String brokerName = broker.getMetadata().getName();
        Pod brokerPod = getClient().getFirstPodByPrefixName(testNamespace, brokerName);
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        LOGGER.info("[{}] Broker {} is up and running with TLS", testNamespace, brokerName);

        // TLS Authentication for netty, but for Artemis as Guest due to JAAS settings
        testTlsMessaging(testNamespace, brokerPod, tlsAddress, brokerUris.get(0), null, clientSecret,
                Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());

        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, tlsAddress);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        if (!singleSecret) {
            getClient().deleteSecret(testNamespace, bugBrokerSecretName);
        }
        getClient().deleteSecret(testNamespace, clientSecret);
    }

}
