/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.multicluster;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.KubeClient;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.TestUtils;
import io.brokerqe.claire.junit.TestMinimumKubernetesCount;
import io.brokerqe.claire.operator.ArtemisCloudClusterOperator;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.brokerqe.claire.scalability.ScalabilityTests;
import io.brokerqe.claire.security.CertificateManager;
import io.brokerqe.claire.security.KeyStoreData;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@TestMinimumKubernetesCount(2)
public class CrossPlatformTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScalabilityTests.class);
    private final String testNamespace = getRandomNamespaceName("cross-tests", 3);
    private KubeClient kubeclient0;
    private KubeClient kubeclient1;
    private List<ArtemisCloudClusterOperator> operators = new ArrayList<>();

    @BeforeAll
    void setupClusterOperator() {
        kubeclient0 = ResourceManager.getKubeClient(0);
        kubeclient1 = ResourceManager.getKubeClient(1);
        setupMulticlusterOperator(kubeclient0, kubeclient1, testNamespace);
    }

    private void setupMulticlusterOperator(KubeClient kc0, KubeClient kc1, String namespace) {
        for (KubeClient kubeclient : List.of(kubeclient0, kubeclient1)) {
            LOGGER.info("[{}] Deploying Operator", namespace);
            setClient(kubeclient);
            getClient().createNamespace(testNamespace, true);
            operators.add(ResourceManager.deployArtemisClusterOperator(testNamespace));
        }
    }

    @AfterAll
    void teardownClusterOperator() {
        for (KubeClient kubeclient : List.of(kubeclient0, kubeclient1)) {
            LOGGER.info("[{}] Teardown Operator", testNamespace);
            setClient(kubeclient);
            for (ArtemisCloudClusterOperator tmpOperator : operators) {
                this.operator = tmpOperator;
                teardownDefaultClusterOperator(testNamespace);
            }
            getClient().deleteNamespace(testNamespace);
        }
    }

    @Test
    void externalMessagingTest() {
        setClient(kubeclient0);
        String brokerSecretName = "broker-tls-secret";
        String clientSecret = "client-tls-secret";
        String amqpAcceptorName = "my-amqp";
        String owireAcceptorName = "my-owire";

        ActiveMQArtemis broker = ResourceManager.createArtemis(testNamespace, "tls-broker");
        ActiveMQArtemisAddress tlsAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName, true);
        Acceptors owireAcceptors = createAcceptor(owireAcceptorName, "openwire", 61618, true, true, brokerSecretName, true);
        Map<String, KeyStoreData> keystores = CertificateManager.generateDefaultCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                ResourceManager.generateDefaultClientDN(),
                List.of(ResourceManager.generateSanDnsNames(broker, List.of(amqpAcceptorName, owireAcceptorName))),
                null
        );

        // One Way TLS
        getClient().createSecretEncodedData(testNamespace, brokerSecretName, CertificateManager.createBrokerKeystoreSecret(keystores));

        broker = addAcceptorsWaitForPodReload(testNamespace, List.of(amqpAcceptors, owireAcceptors), broker);
        String brokerName = broker.getMetadata().getName();
        List<String> brokerUris = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, brokerName + "-" + amqpAcceptorName);
        LOGGER.info("[{}] Broker {} is up and running with TLS", testNamespace, brokerName);

        setClient(kubeclient1);
        // Two Way - Mutual Authentication (Clients TLS secret)
        getClient().createSecretEncodedData(testNamespace, clientSecret, CertificateManager.createClientKeystoreSecret(keystores));
        // TLS Authentication for netty, but for Artemis as Guest due to JAAS settings
        testTlsMessaging(testNamespace, tlsAddress, brokerUris.get(0), null, clientSecret,
                Constants.CLIENT_KEYSTORE_ID, keystores.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());

        getClient().deleteSecret(testNamespace, clientSecret);
        setClient(kubeclient0);
        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, tlsAddress);
        getClient().deleteSecret(testNamespace, brokerSecretName);
    }

    @Test
    void simpleCrossPlatformTest() {
        String broker0Name = "artemis0";
        String broker0SecretName = "broker0-tls-secret-" + TestUtils.getRandomString(3);
        String broker1Name = "artemis1";
        String broker1SecretName = "broker1-tls-secret-" + TestUtils.getRandomString(3);
        String clientSecret = "client-tls-secret-" + TestUtils.getRandomString(3);
        String acceptorName = "all-acceptor";
        Acceptors allAcceptors0 = createAcceptor(acceptorName, "all", 61618, true, true, broker0SecretName, true);
        Acceptors allAcceptors1 = createAcceptor(acceptorName, "all", 33333, true, true, broker1SecretName, true);

        // Deployment on kubernetes0
        setClient(kubeclient0);
        ActiveMQArtemisAddress tlsAddress0 = ResourceManager.createArtemisAddress(testNamespace, "testq", "testq");
        ActiveMQArtemis broker0 = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(broker0Name)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                // POSSIBLE BUG? Issue with TLS login
//                        .withRequireLogin()
                    .endDeploymentPlan()
                    .withAcceptors(allAcceptors0)
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(false)
                    .endConsole()
                .endSpec()
                .build();

        Map<String, KeyStoreData> keystores0 = CertificateManager.generateCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                broker0Name,
                ResourceManager.generateDefaultClientDN(),
                "client0",
                List.of(ResourceManager.generateSanDnsNames(broker0, List.of(acceptorName))),
                null
        );
        getClient().createSecretEncodedData(testNamespace, broker0SecretName, CertificateManager.createBrokerKeystoreSecret(keystores0));
        broker0 = ResourceManager.createArtemis(testNamespace, broker0);
        List<String> brokerUris0 = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, broker0Name + "-" + acceptorName);
        LOGGER.info("[{}] Broker {} is up and running with enabled TLS {}", testNamespace, broker0Name, brokerUris0);

        // Deployment on kubernetes1
        setClient(kubeclient1);
        ActiveMQArtemisAddress tlsAddress1 = ResourceManager.createArtemisAddress(testNamespace, "testq", "testq");
        ActiveMQArtemis broker1 = new ActiveMQArtemisBuilder()
                .editOrNewMetadata()
                    .withName(broker1Name)
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewDeploymentPlan()
                        .withSize(1)
                        .withImage("placeholder")
                    .endDeploymentPlan()
                    .withAcceptors(allAcceptors1)
                    .editOrNewConsole()
                        .withExpose(true)
                        .withSslEnabled(false)
                    .endConsole()
                .endSpec()
                .build();

        Map<String, KeyStoreData> keystores1 = CertificateManager.generateCertificateKeystores(
                ResourceManager.generateDefaultBrokerDN(),
                broker1Name,
                ResourceManager.generateDefaultClientDN(),
                "client1",
                List.of(ResourceManager.generateSanDnsNames(broker1, List.of(acceptorName))),
                null
        );

        getClient().createSecretEncodedData(testNamespace, broker1SecretName, CertificateManager.createBrokerKeystoreSecret(keystores1));
        broker1 = ResourceManager.createArtemis(testNamespace, broker1);
        List<String> brokerUris1 = getClient().getExternalAccessServiceUrlPrefixName(testNamespace, broker1Name + "-" + acceptorName);
        LOGGER.info("[{}] Broker {} is up and running with enabled TLS {}", testNamespace, broker1Name, brokerUris1);

        // ======== test cross platform messaging
        // setClient(kubeclient1);
        LOGGER.info("[{}] Test Cross platform messaging kubeclient1 -> broker0", testNamespace);
        getClient().createSecretEncodedData(testNamespace, clientSecret, CertificateManager.createClientKeystoreSecret(keystores0));
        LOGGER.info("[{}] Starting SystemTest clients AMQP subscriber - publisher test", testNamespace);
        testTlsMessaging(testNamespace, tlsAddress0, brokerUris0.get(0), null, clientSecret,
                Constants.CLIENT_KEYSTORE_ID, keystores0.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores0.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());


        LOGGER.info("[{}] Test Cross platform messaging kubeclient0 -> broker1", testNamespace);
        setClient(kubeclient0);
        getClient().createSecretEncodedData(testNamespace, clientSecret, CertificateManager.createClientKeystoreSecret(keystores1));
        LOGGER.info("[{}] Starting SystemTest clients AMQP subscriber - publisher test", testNamespace);
        testTlsMessaging(testNamespace, tlsAddress1, brokerUris1.get(0), null, clientSecret,
                Constants.CLIENT_KEYSTORE_ID, keystores1.get(Constants.CLIENT_KEYSTORE_ID).getPassword(),
                Constants.CLIENT_TRUSTSTORE_ID, keystores1.get(Constants.CLIENT_TRUSTSTORE_ID).getPassword());

        LOGGER.info("[{}] Cross Messaging tests finished successfully. Tearing down environment", testNamespace);
        setClient(kubeclient1);
        ResourceManager.deleteArtemis(testNamespace, broker1);
        ResourceManager.deleteArtemisAddress(testNamespace, tlsAddress1);
        getClient().deleteSecret(testNamespace, clientSecret);
        getClient().deleteSecret(testNamespace, broker1SecretName);

        setClient(kubeclient0);
        ResourceManager.deleteArtemis(testNamespace, broker0);
        ResourceManager.deleteArtemisAddress(testNamespace, tlsAddress0);
        getClient().deleteSecret(testNamespace, clientSecret);
        getClient().deleteSecret(testNamespace, broker0SecretName);
    }

}
