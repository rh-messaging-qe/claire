/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurity;
import io.amq.broker.v1beta1.ActiveMQArtemisSecurityBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.BrokerBuilder;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.broker.Permissions;
import io.amq.broker.v1beta1.activemqartemissecurityspec.securitysettings.broker.PermissionsBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.Constants;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.helpers.brokerproperties.BPActiveMQArtemisAddress;
import io.brokerqe.claire.junit.TestValidSince;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag(Constants.TAG_TLS)
@Tag(Constants.TAG_JAAS)
public abstract class TLSAuthorizationTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(TLSAuthorizationTests.class);
    String testNamespace;
    String brokerName = "cert-artemis";
    String secretName = "certlogin-jaas-config";
    String brokerSecretName = "broker-tls-secret";
    String clientSecretName = "client-tls-secret";
    String amqpAcceptorName = "my-amqp";
    String producerSecretName = "producer-tls-secret";
    String consumerSecretName = "consumer-tls-secret";
    String browserSecretName = "browser-tls-secret";
    String expiredBeforeSecretName = "expired-before-tls-secret";
    String expiredAfterSecretName = "expired-after-tls-secret";
    String expiredSecretName = "expired-tls-secret";
    int msgsExpected = 2;
    String saslMechanism = ArtemisConstants.SASL_EXTERNAL;
    ActiveMQArtemis broker;
    ActiveMQArtemisSecurity artemisSecurity;
    Acceptors amqpAcceptors;
    BPActiveMQArtemisAddress tlsAddress;
    String tlsAddressName;
    String tlsAddressQueueName;
    BPActiveMQArtemisAddress forbiddenAddress;
    List<String> brokerUris;
    Pod clientsPod;
    Deployment clients;
    Map<String, KeyStoreData> producerKeystores;
    Map<String, KeyStoreData> consumerKeystores;
    Map<String, KeyStoreData> browserKeystores;
    Map<String, KeyStoreData> expiredBeforeKeystores;
    Map<String, KeyStoreData> expiredAfterKeystores;
    Map<String, KeyStoreData> expiredKeystores;
    CertificateData producerCertData;
    CertificateData consumerCertData;
    CertificateData browserCertData;
    CertificateData expiredBeforeCertData;
    CertificateData expiredAfterCertData;
    CertificateData expiredCertData;

    @AfterAll
    void teardownDeployment() {
        ResourceManager.undeployClientsContainer(testNamespace, clients);
        ResourceManager.deleteArtemisSecurity(testNamespace, artemisSecurity);
        ResourceManager.deleteArtemis(testNamespace, broker);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        getClient().deleteSecret(testNamespace, clientSecretName);
        getClient().deleteSecret(testNamespace, producerSecretName);
        getClient().deleteSecret(testNamespace, consumerSecretName);
        getClient().deleteSecret(testNamespace, brokerSecretName);
        teardownDefaultClusterOperator(testNamespace);
    }

    protected void createArtemisDeployment() {
        Map<String, String> jaasData = Map.of(
            ArtemisConstants.LOGIN_CONFIG_CONFIG_KEY, """
                activemq {
                    org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
                        reload=false
                        org.apache.activemq.jaas.properties.user="artemis-users.properties"
                        org.apache.activemq.jaas.properties.role="artemis-roles.properties"
                        baseDir="/home/jboss/amq-broker/etc";

                    org.apache.activemq.artemis.spi.core.security.jaas.TextFileCertificateLoginModule sufficient
                        debug=true
                        org.apache.activemq.jaas.textfiledn.user="cert_users.properties"
                        org.apache.activemq.jaas.textfiledn.role="cert_roles.properties";
                    };
                """,
            "cert_users.properties", """
                producer=C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=producer
                consumer=C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=consumer
                browser=C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=browser
                producerExpired=/C=CZ, L=Brno, O=ArtemisCloud, OU=tls-tests, CN=expired\\\\w*/
                """,
            "cert_roles.properties", """
                producers=producer,producerExpired
                consumers=consumer
                browsers=browser
                """
        );

        // create automagically mounted secret certlogin-jaas-config
        getClient().createSecretStringData(testNamespace, secretName, jaasData, true);

        getClient().createConfigMap(testNamespace, "debug-logging-config",
                Map.of(ArtemisConstants.LOGGING_PROPERTIES_CONFIG_KEY, """
                    appender.stdout.name = STDOUT
                    appender.stdout.type = Console
                    rootLogger = info, STDOUT
                    logger.activemq.name=org.apache.activemq.artemis.spi.core.security.jaas
                    logger.activemq.level=debug
                    logger.activemq.netty.name=activemq-netty
                    logger.activemq.netty.level=info
                """)
        );

        amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672, true, true, brokerSecretName, true);
        // reference secret in the broker CR spec.extraMounts.secrets
        broker = new ActiveMQArtemisBuilder()
            .editOrNewMetadata()
                .withName(brokerName)
                .withNamespace(testNamespace)
            .endMetadata()
            .editOrNewSpec()
                .editOrNewDeploymentPlan()
                    .withSize(1)
                    .withImage("placeholder")
                    .editOrNewExtraMounts()
                        .withSecrets(secretName)
                        .withConfigMaps("debug-logging-config")
                    .endExtraMounts()
                .endDeploymentPlan()
                .withAcceptors(List.of(amqpAcceptors))
        // TODO: do they really work?
                .withBrokerProperties(List.of(
//                    "securityRoles." + addressQueueName + ".producers.send=true",
//                    "securityRoles." + addressQueueName + ".consumers.consume=true",
//                    "securityRoles." + addressQueueName + ".producers.createAddress=true",
//                    "securityRoles." + addressQueueName + ".producers.createNonDurableQueue=true",
//                    "securityRoles." + addressQueueName + ".browsers.browse=true"
                    "acceptorConfigurations." + amqpAcceptorName + ".extraParams.saslMechanisms=EXTERNAL"
                ))
            .endSpec()
            .build();

        List<Permissions> secPerms = List.of(
                new PermissionsBuilder()
                    .withOperationType("send")
                    .withRoles("producers")
                    .build(),
                new PermissionsBuilder()
                    .withOperationType("createAddress")
                    .withRoles("producers")
                    .build(),
                new PermissionsBuilder()
                    .withOperationType("createDurableQueue")
                    .withRoles("producers")
                    .build(),
                new PermissionsBuilder()
                    .withOperationType("consume")
                    .withRoles("consumers")
                    .build(),
                new PermissionsBuilder()
                    .withOperationType("createNonDurableQueue")
                    .withRoles("consumers")
                    .build(),
                new PermissionsBuilder()
                    .withOperationType("browse")
                    .withRoles("browsers")
                    .build()
        );

        artemisSecurity = new ActiveMQArtemisSecurityBuilder()
                .editOrNewMetadata()
                    .withName("rbac-textcert-security")
                    .withNamespace(testNamespace)
                .endMetadata()
                .editOrNewSpec()
                    .editOrNewSecuritySettings()
                        .addToBroker(new BrokerBuilder()
                            .withMatch(tlsAddress.getAddressName())
                            .withPermissions(secPerms)
                            .build())
                    .endSecuritySettings()
                .endSpec()
                .build();

        ResourceManager.createArtemisSecurity(testNamespace, artemisSecurity);

    }

    @Test
    public void testTextCertificateRBAC() {
        LOGGER.info("[{}] Test producer & consumer expected permissions", testNamespace);
        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                producerKeystores.get(producerCertData.getAlias() + ".ks"),
                producerKeystores.get(producerCertData.getAlias() + ".ts"), producerSecretName);
        int sentCount = producer.sendMessages();

        MessagingClient consumer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                consumerKeystores.get(consumerCertData.getAlias() + ".ks"),
                consumerKeystores.get(consumerCertData.getAlias() + ".ts"), consumerSecretName);
        int receivedCount = consumer.receiveMessages();

        assertThat(sentCount, equalTo(msgsExpected));
        assertThat(receivedCount, equalTo(msgsExpected));
        assertThat(consumer.compareMessages(producer.getSentMessages(), consumer.getReceivedMessages()), is(true));
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    public void testProducerTextCertificateRBAC() {
        LOGGER.info("[{}] Test producer permissions", testNamespace);

        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddress, msgsExpected, saslMechanism,
                producerKeystores.get(producerCertData.getAlias() + ".ks"),
                producerKeystores.get(producerCertData.getAlias() + ".ts"), producerSecretName);
        int sentCount = producer.sendMessages();
        assertThat(sentCount, equalTo(msgsExpected));
        Throwable t = assertThrows(MessagingClientException.class, producer::receiveMessages);
        assertThat(t.getMessage(), containsString("does not have permission='CONSUME'"));
        assertThat(producer.compareMessages(producer.getSentMessages(), producer.getReceivedMessages()), is(false));

        // try to send messages to not allowed address
        MessagingClient producerForbidden = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), forbiddenAddress, msgsExpected, saslMechanism,
                producerKeystores.get(producerCertData.getAlias() + ".ks"),
                producerKeystores.get(producerCertData.getAlias() + ".ts"), producerSecretName);
        t = assertThrows(MessagingClientException.class, producerForbidden::sendMessages);
        assertThat(t.getMessage(), containsString("does not have permission='SEND'"));
        t = assertThrows(MessagingClientException.class, producerForbidden::receiveMessages);
        assertThat(t.getMessage(), containsString("does not have permission='CONSUME'"));
    }

    @Test
    @TestValidSince(ArtemisVersion.VERSION_2_28)
    public void testConsumerTextCertificateRBAC() {
        MessagingClient producerBug = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddress, msgsExpected, saslMechanism,
                producerKeystores.get(producerCertData.getAlias() + ".ks"),
                producerKeystores.get(producerCertData.getAlias() + ".ts"), producerSecretName);
        int sentCount = producerBug.sendMessages();

//        ENTMQBR-7720 TestUtils.threadSleep(Constants.DURATION_10_SECONDS + 1000);
        LOGGER.info("[{}] Test consumer permissions", testNamespace);
        MessagingClient consumer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddress, msgsExpected, saslMechanism,
                consumerKeystores.get(consumerCertData.getAlias() + ".ks"),
                consumerKeystores.get(consumerCertData.getAlias() + ".ts"), consumerSecretName);
        Throwable t = assertThrows(MessagingClientException.class, consumer::sendMessages);
        assertThat(t.getMessage(), containsString("does not have permission='SEND'"));


        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddress, msgsExpected, saslMechanism,
                producerKeystores.get(producerCertData.getAlias() + ".ks"),
                producerKeystores.get(producerCertData.getAlias() + ".ts"), producerSecretName);
        producer.sendMessages();

        int receivedCount = consumer.receiveMessages();
        assertThat(receivedCount, equalTo(msgsExpected));
        assertThat(sentCount, equalTo(msgsExpected));

        // try to consume from not allowed queue
        MessagingClient consumerForbidden = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), forbiddenAddress, msgsExpected, saslMechanism,
                consumerKeystores.get(consumerCertData.getAlias() + ".ks"),
                consumerKeystores.get(consumerCertData.getAlias() + ".ts"), consumerSecretName);
        t = assertThrows(MessagingClientException.class, consumerForbidden::sendMessages);
        assertThat(t.getMessage(), containsString("does not have permission='SEND'"));
        t = assertThrows(MessagingClientException.class, consumerForbidden::receiveMessages);
        assertThat(t.getMessage(), containsString("does not have permission='CONSUME'"));
    }

    @RetryingTest(maxAttempts = 3, suspendForMs = 10000)
    public void testExpiredCertificateBefore() {
        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                expiredBeforeKeystores.get(expiredBeforeCertData.getAlias() + ".ks"),
                expiredBeforeKeystores.get(expiredBeforeCertData.getAlias() + ".ts"), expiredBeforeSecretName);
        Throwable t = assertThrows(MessagingClientException.class, producer::sendMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
        t = assertThrows(MessagingClientException.class, producer::receiveMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
    }

    @RetryingTest(maxAttempts = 3, suspendForMs = 10000)
    public void testExpiredCertificateAfter() {
        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                expiredAfterKeystores.get(expiredAfterCertData.getAlias() + ".ks"),
                expiredAfterKeystores.get(expiredAfterCertData.getAlias() + ".ts"), expiredAfterSecretName);
        Throwable t = assertThrows(MessagingClientException.class, producer::sendMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
        t = assertThrows(MessagingClientException.class, producer::receiveMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
    }

    @Test
    public void testExpiredCertificate() {
        MessagingClient producer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                expiredKeystores.get(expiredCertData.getAlias() + ".ks"),
                expiredKeystores.get(expiredCertData.getAlias() + ".ts"), expiredSecretName);
        Throwable t = assertThrows(MessagingClientException.class, producer::sendMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
        t = assertThrows(MessagingClientException.class, producer::receiveMessages);
        assertThat(t.getMessage(), containsString("has failed due to: javax.net.ssl.SSLHandshakeException"));
    }

    @AfterEach
    void cleanQueue() {
        LOGGER.info("[{}] Cleaning queue {}", testNamespace, tlsAddress.getAddressName());
        MessagingClient consumer = ResourceManager.createMessagingClientTls(clientsPod,
                brokerUris.get(0), tlsAddressName, tlsAddressQueueName, msgsExpected, saslMechanism,
                consumerKeystores.get(consumerCertData.getAlias() + ".ks"),
                consumerKeystores.get(consumerCertData.getAlias() + ".ts"), consumerSecretName);
        try {
            consumer.receiveMessages();
        } catch (Exception exception) {
            // whatever happens here, move on
        }
    }

}
