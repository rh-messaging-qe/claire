/*
 * Copyright Broker QE authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.brokerqe.claire.security;

import io.amq.broker.v1beta1.ActiveMQArtemis;
import io.amq.broker.v1beta1.ActiveMQArtemisAddress;
import io.amq.broker.v1beta1.ActiveMQArtemisBuilder;
import io.amq.broker.v1beta1.activemqartemisspec.Acceptors;
import io.brokerqe.claire.AbstractSystemTests;
import io.brokerqe.claire.ArtemisConstants;
import io.brokerqe.claire.ArtemisVersion;
import io.brokerqe.claire.ResourceManager;
import io.brokerqe.claire.clients.ClientType;
import io.brokerqe.claire.clients.MessagingClient;
import io.brokerqe.claire.clients.MessagingClientException;
import io.brokerqe.claire.junit.TestValidSince;
import io.brokerqe.claire.operator.ArtemisFileProvider;
import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestValidSince(ArtemisVersion.VERSION_2_28)
public class LdapTests extends AbstractSystemTests {

    private static final Logger LOGGER = LoggerFactory.getLogger(LdapTests.class);
    private final String testNamespace = getRandomNamespaceName("ldap-tests", 3);
    private Openldap openldap;
    String secretName = "ldaplogin-jaas-config";
    String brokerName = "artemis";
    String amqpAcceptorName = "my-amqp";
    ActiveMQArtemis broker;
    ActiveMQArtemisAddress ldapAddress;
    Pod brokerPod;
    String allDefaultPort;
    final boolean jwtTokenSupported = false;

    Map<String, String> users = Map.of(
            ArtemisConstants.ALICE_NAME, ArtemisConstants.ALICE_PASS,
            ArtemisConstants.BOB_NAME, ArtemisConstants.BOB_PASS,
            ArtemisConstants.CHARLIE_NAME, ArtemisConstants.CHARLIE_PASS,
            ArtemisConstants.LALA_NAME, ArtemisConstants.LALA_PASS);

    @BeforeAll
    void setupClusterOperator() {
        setupDefaultClusterOperator(testNamespace);
        openldap = ResourceManager.getOpenldapInstance(testNamespace);
        openldap.deployLdap();
        setupEnvironment();
    }

    @AfterAll
    void teardownClusterOperator() {
        ResourceManager.deleteArtemis(testNamespace, broker);
        ResourceManager.deleteArtemisAddress(testNamespace, ldapAddress);
        openldap.undeployLdap();
        teardownDefaultClusterOperator(testNamespace);
    }

    private void setupEnvironment() {
        Map<String, String> jaasData = Map.of(
            ArtemisConstants.LOGIN_CONFIG_CONFIG_KEY, """
                activemq {
                    org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule sufficient
                        reload=false
                        org.apache.activemq.jaas.properties.user="artemis-users.properties"
                        org.apache.activemq.jaas.properties.role="artemis-roles.properties"
                        baseDir="/home/jboss/amq-broker/etc";

                     org.apache.activemq.artemis.spi.core.security.jaas.LDAPLoginModule sufficient
                         debug=true
                         initialContextFactory=com.sun.jndi.ldap.LdapCtxFactory
                         connectionURL="ldap://openldap:1389"
                         connectionUsername="cn=admin,dc=example,dc=org"
                         connectionPassword=admin
                         connectionProtocol=s
                         authentication=simple
                         userBase="ou=users,dc=example,dc=org"
                         userSearchMatching="(cn={0})"
                         userSearchSubtree=false
                         roleBase="ou=users,dc=example,dc=org"
                         roleName=cn
                         roleSearchMatching="(member=cn={1},ou=users,dc=example,dc=org)"
                         roleSearchSubtree=false
                         ;
                    };
                """
        );
        // create automagically mounted secret ldaplogin-jaas-config
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

        // reference secret in the broker CR spec.extraMounts.secrets
        Acceptors amqpAcceptors = createAcceptor(amqpAcceptorName, "amqp", 5672);
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
                .withBrokerProperties(List.of(
                    "securityRoles.#.producers.send=true",
                    "securityRoles.#.consumers.consume=true",
                    "securityRoles.#.producers.createAddress=true",
                    "securityRoles.#.producers.createNonDurableQueue=true"
                        ))
            .endSpec()
            .build();
        broker = ResourceManager.createArtemis(testNamespace, broker);
        ldapAddress = ResourceManager.createArtemisAddress(testNamespace, ArtemisFileProvider.getAddressQueueExampleFile());
        brokerPod = getClient().listPodsByPrefixName(testNamespace, brokerName).get(0);
        allDefaultPort = getServicePortNumber(testNamespace, getArtemisServiceHdls(testNamespace, broker), "all");
    }

    public boolean isJwtTokenSupported() {
        return jwtTokenSupported;
    }

    String getJwtToken(String username) {
        LOGGER.error("LDAP does not support JWT tokens!");
        return null;
    }

    String getPassword(String username) {
        if (isJwtTokenSupported()) {
            return getJwtToken(username);
        } else {
            LOGGER.debug("LDAP does not support JWT tokens. Using plain password.");
            return users.get(username);
        }
    }

    String getTestNamespace() {
        return testNamespace;
    }

    @Test
    public void testSenderReceiverLdapUsers() {
        int messages = 5;
        String alicePass = getPassword(ArtemisConstants.ALICE_NAME);
        String bobPass = getPassword(ArtemisConstants.BOB_NAME);

        LOGGER.info("[{}] Trying to send messages as {} with {}", getTestNamespace(), ArtemisConstants.ALICE_NAME, alicePass);
        MessagingClient producerAlice = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod,
                allDefaultPort, ldapAddress, messages, ArtemisConstants.ALICE_NAME, alicePass);
        int sentAlice = producerAlice.sendMessages();

        LOGGER.info("[{}] Trying to receive messages as {} with {}", getTestNamespace(), ArtemisConstants.ALICE_NAME, alicePass);
        Throwable t = assertThrows(MessagingClientException.class, producerAlice::receiveMessages);
        assertThat(t.getMessage(), containsString("does not have permission='CONSUME' for queue"));

        LOGGER.info("[{}] Trying to receive messages as {} with {}", getTestNamespace(), ArtemisConstants.BOB_NAME, bobPass);
        MessagingClient consumerBob = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod,
                allDefaultPort, ldapAddress, messages, ArtemisConstants.BOB_NAME, bobPass);
        int consumedBob = consumerBob.receiveMessages();
        assertThat(sentAlice, equalTo(messages));
        assertThat(sentAlice, equalTo(consumedBob));

        LOGGER.info("[{}] Trying to send messages as {} with {}", getTestNamespace(), ArtemisConstants.BOB_NAME, bobPass);
        t = assertThrows(MessagingClientException.class, consumerBob::sendMessages);
        assertThat(t.getMessage(), containsString("does not have permission='SEND' on address"));
    }

    @Test
    public void testSingleUserWithPermissions() {
        int messages = 5;
        String charliePass = getPassword(ArtemisConstants.CHARLIE_NAME);
        LOGGER.info("[{}] Send & receive messages as {} with {}", getTestNamespace(), ArtemisConstants.CHARLIE_NAME, charliePass);
        MessagingClient clientCharlie = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod,
                allDefaultPort, ldapAddress, messages, ArtemisConstants.CHARLIE_NAME, charliePass);
        int sent = clientCharlie.sendMessages();
        int received = clientCharlie.receiveMessages();
        assertThat(sent, equalTo(messages));
        assertThat(sent, equalTo(received));
    }

    @Test
    public void testSingleUserWithPermissionsAmqp() {
        int messages = 5;
        String charliePass = getPassword(ArtemisConstants.CHARLIE_NAME);
        LOGGER.info("[{}] Send & receive amqp messages as {} with {}", getTestNamespace(), ArtemisConstants.CHARLIE_NAME, charliePass);
        testMessaging(ClientType.BUNDLED_AMQP, getTestNamespace(), brokerPod, ldapAddress, messages, ArtemisConstants.CHARLIE_NAME, charliePass);
    }

    @Test
    public void testWrongUser() {
        int messages = 2;
        // invalid user
        LOGGER.info("[{}] Trying to connect as invalid user {} with {}", getTestNamespace(), ArtemisConstants.LALA_NAME, ArtemisConstants.LALA_PASS);
        MessagingClient wrongUser = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod,
                allDefaultPort, ldapAddress, messages, ArtemisConstants.LALA_NAME, ArtemisConstants.LALA_PASS);
        Throwable t = assertThrows(MessagingClientException.class, wrongUser::sendMessages);
        assertThat(t.getMessage(), containsString("Unable to validate user from"));

        // incorrect pass
        LOGGER.info("[{}] Trying to connect as {} with invalid password {}", getTestNamespace(), ArtemisConstants.CHARLIE_NAME, ArtemisConstants.LALA_PASS);
        wrongUser = ResourceManager.createMessagingClient(ClientType.BUNDLED_CORE, brokerPod,
                allDefaultPort, ldapAddress, messages, ArtemisConstants.CHARLIE_NAME, ArtemisConstants.LALA_PASS);
        t = assertThrows(MessagingClientException.class, wrongUser::sendMessages);
        assertThat(t.getMessage(), containsString("Unable to validate user from"));
    }
}
